package se.modfin.betterpress;

import com.google.common.io.Closeables;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.resource.ClassPathResource;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;

import static spark.Spark.*;

public class BetterPress {
    private static Logger log = LoggerFactory.getLogger(BetterPress.class);

    public static void main(String[] args) {
        port(8080);

        before((req, res) -> res.type("application/pdf"));
        post("", BetterPress::generatePDF);
        post("/", BetterPress::generatePDF);

        ClassPathResource resource = new ClassPathResource("app.properties");
        Properties p = new Properties();
        InputStream inputStream = null;
        try {
            inputStream = resource.getInputStream();
            p.load(inputStream);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            Closeables.closeQuietly(inputStream);
        }
        log.info("Running Betterpress " + p.getProperty("version"));
    }

    private static HttpServletResponse generatePDF(Request req, Response res) {
        MultipartConfigElement multipartConfigElement = new MultipartConfigElement("/tmp");
        req.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement);

        FileSystem fs;
        String fsId = UUID.randomUUID().toString();
        try {
            fs = generateInMemoryFileSystemFromParts(fsId, req.raw().getParts());
        } catch (Exception e) {
            final String msg = "could not generate PDF";
            log.error(msg, e);
            halt(400, msg);
            return res.raw();
        }

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        String indexFilePath = "/" + fsId + "/index.html";
        String indexFileContents;
        try {
            indexFileContents = new String(Files.readAllBytes(fs.getPath(indexFilePath)));
        } catch (IOException e) {
            final String msg = "could not read index file, make sure to send one file called 'index.html'";
            log.error(msg, e);
            halt(400, msg);
            return res.raw();
        }
        org.w3c.dom.Document doc = html5ParseDocument(indexFileContents);
        URL url;
        try {
            url = fs.getPath(indexFilePath).toUri().toURL();
        } catch (MalformedURLException e) {
            final String msg = "could not generate PDF";
            log.error(msg, e);
            halt(500, msg);
            return res.raw();
        }
        builder.withW3cDocument(doc, url.toString());

        try (OutputStream os = res.raw().getOutputStream()) {
            builder.toStream(os);
            builder.run();
        } catch (Exception e) {
            final String msg = "could not generate PDF";
            log.error(msg, e);
            halt(500, msg);
        } finally {
            try {
                fs.close();
            } catch (IOException e) {
                log.error("tried to close jimfs, but failed. probably unrecoverable", e);
                System.exit(1);
            }
        }
        return res.raw();
    }

    /**
     * Generate an in-memory file-system from file parts
     *
     * @param uniqueHash base dir for the file system ("/{uniqueHash}")
     * @param parts      file parts e.g. from multipart form data
     * @return generated file system. Implements Closeable, i.e. run close() on it to dispose
     */
    private static FileSystem generateInMemoryFileSystemFromParts(String uniqueHash, Collection<Part> parts) throws IOException {
        FileSystem fs = Jimfs.newFileSystem(uniqueHash, Configuration.unix());
        Path dir = fs.getPath("/" + uniqueHash);
        try {
            Files.createDirectory(dir);
            for (Part part : parts) {
                Path path = dir.resolve(part.getName());
                InputStream data = part.getInputStream();
                Files.copy(data, path);
                log.info("added file: " + path + " to in memory file system");
            }
        } catch (IOException e) {
            try {
                fs.close();
            } catch (IOException e2) {
                log.error("in memory filesystem error", e2);
                log.error("write was:");
                log.error("tried to close jimfs after write error, but failed. probably unrecoverable", e);
                System.exit(1);
            }
            throw e;
        }
        return fs;
    }

    private static org.w3c.dom.Document html5ParseDocument(String html) {
        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        return new W3CDom().fromJsoup(doc);
    }
}
