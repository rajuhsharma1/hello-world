package com.ecal.catalog.internal.importing;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CatalogImportController {

  private final CatalogImportService imports;

  public CatalogImportController(CatalogImportService imports) {
    this.imports = imports;
  }

  @PostMapping(value = "/api/catalog/import", consumes = "multipart/form-data")
  public CatalogImportService.Result upload(
    @RequestParam("file") MultipartFile file
  ) {
    return imports.run(file);
  }
}
