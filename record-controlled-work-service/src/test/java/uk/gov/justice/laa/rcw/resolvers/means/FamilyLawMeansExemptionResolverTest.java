package uk.gov.justice.laa.rcw.resolvers.means;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;

class FamilyLawMeansExemptionResolverTest {

  private final FamilyLawMeansExemptionResolver resolver =
      new FamilyLawMeansExemptionResolver(
          new PublicFamilyMeansExemptionCalculator(), new PrivateFamilyMeansExemptionCalculator());

  @ParameterizedTest
  @MethodSource("unclassifiedAnswers")
  void shouldRequireMeansAssessmentWithoutFamilyClassification(ScopingQuestions answers) {
    assertThat(resolver.isMeansAssessmentRequired(answers)).isTrue();
  }

  static Stream<ScopingQuestions> unclassifiedAnswers() {
    return Stream.of(
        null,
        new ScopingQuestions(),
        new ScopingQuestions()
            .needsAdviceOnEUOrInternationalMaintenance(true)
            .careProceedingsStarted(true)
            .careProceedingsParentalResponsibility(true));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false})
  void shouldRequireMeansAssessmentWithoutPrivateFamilyExemption(Boolean maintenanceAdvice) {
    ScopingQuestions answers =
        new ScopingQuestions()
            .familyLawClassification(FamilyLawClassification.PRIVATE)
            .needsAdviceOnEUOrInternationalMaintenance(maintenanceAdvice)
            .careProceedingsStarted(true)
            .careProceedingsParentalResponsibility(true);

    assertThat(resolver.isMeansAssessmentRequired(answers)).isTrue();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "false, true",
        "false, false",
        "false, null",
        "true, false",
        "true, null",
        "null, true",
        "null, false",
        "null, null"
      },
      nullValues = "null")
  void shouldRequireMeansAssessmentWithoutConfirmedPublicFamilyExemption(
      Boolean careProceedings, Boolean parentalResponsibility) {
    ScopingQuestions answers =
        new ScopingQuestions()
            .familyLawClassification(FamilyLawClassification.PUBLIC)
            .careProceedingsStarted(careProceedings)
            .careProceedingsParentalResponsibility(parentalResponsibility)
            .needsAdviceOnEUOrInternationalMaintenance(true);

    assertThat(resolver.isMeansAssessmentRequired(answers)).isTrue();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false, true})
  void shouldExemptPublicFamilyWithCareProceedingsAndParentalResponsibility(
      Boolean irrelevantAnswer) {
    ScopingQuestions answers =
        new ScopingQuestions()
            .familyLawClassification(FamilyLawClassification.PUBLIC)
            .careProceedingsStarted(true)
            .careProceedingsParentalResponsibility(true)
            .needsAdviceOnEUOrInternationalMaintenance(irrelevantAnswer);

    assertThat(resolver.isMeansAssessmentRequired(answers)).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false, true})
  void shouldExemptPrivateFamilyWithInternationalMaintenanceAdvice(Boolean irrelevantAnswer) {
    ScopingQuestions answers =
        new ScopingQuestions()
            .familyLawClassification(FamilyLawClassification.PRIVATE)
            .needsAdviceOnEUOrInternationalMaintenance(true)
            .careProceedingsStarted(irrelevantAnswer)
            .careProceedingsParentalResponsibility(irrelevantAnswer);

    assertThat(resolver.isMeansAssessmentRequired(answers)).isFalse();
  }
}
