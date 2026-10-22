package com.ecal;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  private static final com.tngtech.archunit.core.domain.JavaClasses CODE =
    new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("com.ecal");

  @Test
  void aggregateDoesNotDependOnFrameworksOrPersistence() {
    noClasses()
      .that()
      .resideInAPackage("..order.domain..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage(
        "org.springframework..",
        "java.sql..",
        "javax.sql..",
        "jakarta.persistence..",
        "com.ecal..internal..",
        "com.ecal..web.."
      )
      .check(CODE);
  }

  @Test
  void otherModulesCannotReachAnInternalAdapter() {
    for (String module : new String[] {
      "catalog",
      "inventory",
      "payment",
      "notification",
      "order",
    }) {
      noClasses()
        .that()
        .resideOutsideOfPackage("com.ecal." + module + "..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.ecal." + module + ".internal..")
        .check(CODE);
    }
  }

  @Test
  void domainModulesDoNotFormDependencyCycles() {
    slices().matching("com.ecal.(*)..").should().beFreeOfCycles().check(CODE);
  }
}
