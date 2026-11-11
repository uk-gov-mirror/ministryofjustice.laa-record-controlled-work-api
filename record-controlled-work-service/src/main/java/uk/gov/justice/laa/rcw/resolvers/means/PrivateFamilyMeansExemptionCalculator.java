package uk.gov.justice.laa.rcw.resolvers.means;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;

/** Calculates the private family-law means-assessment requirement. */
@Component
public class PrivateFamilyMeansExemptionCalculator {

  /** Requires assessment unless international maintenance advice confirms an exemption. */
  public boolean isMeansAssessmentRequired(ScopingQuestions answers) {
    return !Boolean.TRUE.equals(answers.getNeedsAdviceOnEUOrInternationalMaintenance());
  }
}
