package com.ecal.distributed.system;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;

class BoundariesTest {

  @Test
  void servicesCannotImportEachOthersImplementation() {
    var code = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("com.ecal.distributed");
    for (String service : new String[] {
      "orders",
      "inventory",
      "payment",
      "reporting",
    })
      for (String other : new String[] {
        "orders",
        "inventory",
        "payment",
        "reporting",
      })
        if (!service.equals(other)) noClasses()
          .that()
          .resideInAPackage("com.ecal.distributed." + service + "..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.ecal.distributed." + other + "..")
          .check(code);
  }
}
