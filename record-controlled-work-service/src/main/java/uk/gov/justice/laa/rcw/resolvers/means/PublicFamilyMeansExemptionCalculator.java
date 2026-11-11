package uk.gov.justice.laa.rcw.resolvers.means;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;

/** Calculates the public family-law means-assessment requirement. */
@Component
public class PublicFamilyMeansExemptionCalculator {

  /** Requires assessment unless both care-proceedings answers confirm an exemption. */
  public boolean isMeansAssessmentRequired(ScopingQuestions answers) {
    return !Boolean.TRUE.equals(answers.getCareProceedingsStarted())
        || !Boolean.TRUE.equals(answers.getCareProceedingsParentalResponsibility());
  }
}
