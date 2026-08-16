package zw.co.hyperfeeds.catalogue;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/catalogue/import")
class CatalogueImportController {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final CatalogueImportService importer;
    CatalogueImportController(CatalogueImportService importer) { this.importer = importer; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CatalogueImportService.ImportResult upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"CSV file is empty");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"CSV file exceeds 5 MB");
        return importer.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", ""));
    }
}
