package uk.gov.justice.laa.rcw.resolvers.means;

import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;

/** Resolves the means-assessment requirement for the supported category of law. */
@Component
@RequiredArgsConstructor
public class MeansExemptionResolver {

  private final FamilyLawMeansExemptionResolver familyLawResolver;

  /** Resolves submitted answers for the currently supported family-law category. */
  @Named("fromSubmittedScopingQuestions")
  public boolean fromSubmittedScopingQuestions(ScopingQuestions answers) {
    return familyLawResolver.isMeansAssessmentRequired(answers);
  }
}
