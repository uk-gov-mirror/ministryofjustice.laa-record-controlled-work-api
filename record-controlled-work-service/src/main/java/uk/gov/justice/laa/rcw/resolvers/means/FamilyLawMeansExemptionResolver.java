package uk.gov.justice.laa.rcw.resolvers.means;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;

/** Selects the family-law rule for the submitted scoping answers. */
@Component
@RequiredArgsConstructor
public class FamilyLawMeansExemptionResolver {

  private final PublicFamilyMeansExemptionCalculator publicCalculator;
  private final PrivateFamilyMeansExemptionCalculator privateCalculator;

  /**
   * Requires a means assessment unless the active family branch confirms an exemption.
   *
   * @param answers nullable submitted scoping answers
   * @return whether a means assessment is required
   */
  public boolean isMeansAssessmentRequired(ScopingQuestions answers) {
    if (answers == null) {
      return true;
    }
    if (answers.getFamilyLawClassification() == FamilyLawClassification.PUBLIC) {
      return publicCalculator.isMeansAssessmentRequired(answers);
    }
    if (answers.getFamilyLawClassification() == FamilyLawClassification.PRIVATE) {
      return privateCalculator.isMeansAssessmentRequired(answers);
    }
    return true;
  }
}
