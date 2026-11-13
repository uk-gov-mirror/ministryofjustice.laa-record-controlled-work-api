package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ClientDetails;
import uk.gov.justice.laa.ia.datastore.client.model.DeclarationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.EligibilityAssessment;
import uk.gov.justice.laa.ia.datastore.client.model.EligibilityData;
import uk.gov.justice.laa.ia.datastore.client.model.EvidenceResponse;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestValidationException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.ApplicationState;

@ExtendWith(MockitoExtension.class)
class ApplicationUpdateServiceTest {

  private static final String AUTHORIZED_OFFICE_CODE = "AB12CD";

  @Mock private ApplicationMapper mockApplicationMapper;
  @Mock private ApplicationGateway mockApplicationGateway;
  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private ApplicationGuard applicationGuard;
  private ApplicationUpdateService applicationUpdateService;

  @BeforeEach
  void setUp() {
    applicationGuard = new ApplicationGuard(mockAuthorizedOfficesProvider);
    applicationUpdateService =
        new ApplicationUpdateService(
            mockApplicationMapper, mockApplicationGateway, applicationGuard);
    lenient()
        .when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of(AUTHORIZED_OFFICE_CODE));
    lenient()
        .when(mockApplicationMapper.toDatastoreApplicationState(any()))
        .thenReturn(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED);
  }

  @Test
  void shouldNotUpdateStatus_whenSubmissionDataMissing() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationRequestValidationException.class)
        .extracting("reason")
        .isEqualTo("APPLICATION_INCOMPLETE");

    verify(mockApplicationGateway, never()).updateApplication(any(), any());
  }

  @Test
  void shouldUpdateDraftStatus_whenSubmissionDataMissing() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    when(mockApplicationMapper.toDatastoreApplicationState(ApplicationState.DRAFT))
        .thenReturn(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT);

    applicationUpdateService.updateStatus(applicationId, ApplicationState.DRAFT);

    ArgumentCaptor<UpdateApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateApplicationCommand.class);
    verify(mockApplicationGateway).updateApplication(eq(applicationId), commandCaptor.capture());
    assertThat(commandCaptor.getValue().getApplicationState())
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "client,true",
        "scopingQuestions,true",
        "familyLawClassification,true",
        "malformedScopingQuestions,true",
        "declaration,true",
        "means,true",
        "meansData,true",
        "evidence,true",
        "client,false",
        "scopingQuestions,false",
        "familyLawClassification,false",
        "malformedScopingQuestions,false",
        "declaration,false",
        "means,null",
        "meansData,null",
        "evidence,null"
      },
      nullValues = "null")
  void shouldNotUpdateStatus_whenRequiredSectionMissing(
      String missingSection, Boolean meansAssessmentRequired) {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    ApplicationResponse application = applicationWithSubmissionData(1L);
    application.setMeansAssessmentRequired(meansAssessmentRequired);
    switch (missingSection) {
      case "client" -> application.setClient(null);
      case "scopingQuestions" -> application.setScopingQuestions(null);
      case "familyLawClassification" -> application.setScopingQuestions(Map.of());
      case "malformedScopingQuestions" -> application.setScopingQuestions("legacy value");
      case "declaration" -> application.setDeclaration(null);
      case "means" -> application.setEligibilityAssessment(null);
      case "meansData" -> application.getEligibilityAssessment().setData(null);
      case "evidence" -> application.setEvidence(null);
      default -> throw new IllegalArgumentException(missingSection);
    }
    when(mockApplicationGateway.fetchApplication(applicationId)).thenReturn(application);

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationRequestValidationException.class)
        .extracting("reason")
        .isEqualTo("APPLICATION_INCOMPLETE");

    verify(mockApplicationGateway, never()).updateApplication(any(), any());
  }

  @ParameterizedTest
  @CsvSource(
      value = {"true", "false", "null"},
      nullValues = "null")
  void shouldUpdateStatus_whenRequiredSectionsPresent(Boolean meansAssessmentRequired) {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    ApplicationResponse application = applicationWithSubmissionData(1L);
    application.setMeansAssessmentRequired(meansAssessmentRequired);
    if (Boolean.FALSE.equals(meansAssessmentRequired)) {
      application.setEligibilityAssessment(null);
      application.setEvidence(null);
    }
    when(mockApplicationGateway.fetchApplication(applicationId)).thenReturn(application);

    applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED);

    verify(mockApplicationGateway).updateApplication(eq(applicationId), any());
  }

  @Test
  void shouldNotUpdateStatus_whenRefreshedApplicationMissingSubmissionData() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    ApplicationResponse refreshedApplication = applicationWithSubmissionData(2L);
    refreshedApplication.setEvidence(null);
    when(mockApplicationGateway.fetchApplication(applicationId))
        .thenReturn(applicationWithSubmissionData(1L), refreshedApplication);
    doThrow(new ApplicationConflictException("Version conflict", "APPLICATION_VERSION_CONFLICT"))
        .when(mockApplicationGateway)
        .updateApplication(eq(applicationId), any());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationRequestValidationException.class)
        .extracting("reason")
        .isEqualTo("APPLICATION_INCOMPLETE");

    verify(mockApplicationGateway, times(2)).fetchApplication(applicationId);
    verify(mockApplicationGateway).updateApplication(eq(applicationId), any());
  }

  @Test
  void shouldUpdateStatus_fetchesETagAndForwardsStatusToDatastore() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(applicationWithSubmissionData(7L));

    applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED);

    verify(mockApplicationGateway).fetchApplication(eq(applicationId));

    ArgumentCaptor<UpdateApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateApplicationCommand.class);
    verify(mockApplicationGateway).updateApplication(eq(applicationId), commandCaptor.capture());
    assertThat(commandCaptor.getValue().geteTag()).isEqualTo(7L);
    assertThat(commandCaptor.getValue().getApplicationState())
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED);
  }

  @Test
  void shouldUpdateStatus_retriesOnceWithFreshETag_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(applicationWithSubmissionData(1L), applicationWithSubmissionData(2L));
    doThrow(
            new ApplicationConflictException(
                "Application %s was modified concurrently".formatted(applicationId)))
        .doNothing()
        .when(mockApplicationGateway)
        .updateApplication(eq(applicationId), any());

    applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED);

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    ArgumentCaptor<UpdateApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateApplicationCommand.class);
    verify(mockApplicationGateway, times(2))
        .updateApplication(eq(applicationId), commandCaptor.capture());
    assertThat(commandCaptor.getAllValues())
        .extracting(UpdateApplicationCommand::geteTag)
        .containsExactly(1L, 2L);
  }

  @Test
  void shouldUpdateDeclaration_fetchesETagAndForwardsDeclarationFieldsToDatastore() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    LocalDate dateSigned = LocalDate.of(2026, 8, 14);
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(7L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());

    applicationUpdateService.updateDeclaration(applicationId, true, dateSigned, "123456/123");

    verify(mockApplicationGateway).fetchApplication(eq(applicationId));
    ArgumentCaptor<EditApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(EditApplicationCommand.class);
    verify(mockApplicationGateway).editApplication(eq(applicationId), commandCaptor.capture());
    assertThat(commandCaptor.getValue().geteTag()).isEqualTo(7L);
    assertThat(commandCaptor.getValue().getUfn()).isEqualTo("123456/123");
    assertThat(commandCaptor.getValue().getDeclaration().getDeclarationConfirmation()).isTrue();
    assertThat(commandCaptor.getValue().getDeclaration().getDateSigned()).isEqualTo(dateSigned);
  }

  @Test
  void shouldUpdateDeclaration_retriesOnceWithFreshETag_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build())
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(2L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    when(mockApplicationGateway.editApplication(eq(applicationId), any()))
        .thenThrow(
            new ApplicationConflictException("Version conflict", "APPLICATION_VERSION_CONFLICT"))
        .thenReturn("\"3\"");

    applicationUpdateService.updateDeclaration(
        applicationId, true, LocalDate.of(2026, 8, 14), "123456/123");

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    ArgumentCaptor<EditApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(EditApplicationCommand.class);
    verify(mockApplicationGateway, times(2))
        .editApplication(eq(applicationId), commandCaptor.capture());
    assertThat(commandCaptor.getAllValues())
        .extracting(EditApplicationCommand::geteTag)
        .containsExactly(1L, 2L);
    assertThat(commandCaptor.getAllValues())
        .allSatisfy(command -> {
          assertThat(command.getDeclaration().getDateSigned()).isEqualTo(LocalDate.of(2026, 8, 14));
          assertThat(command.getDeclaration().getDeclarationConfirmation()).isTrue();
          assertThat(command.getUfn()).isEqualTo("123456/123");
        });
  }

  @Test
  void shouldUpdateDeclaration_throwsApplicationConflictException_whenConflictPersistsAfterRetry() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    ApplicationConflictException datastoreFailure =
        new ApplicationConflictException(
            "Application %s was modified concurrently".formatted(applicationId),
            "APPLICATION_VERSION_CONFLICT");
    doThrow(datastoreFailure)
        .when(mockApplicationGateway)
        .editApplication(eq(applicationId), any());

    assertThatThrownBy(() ->
            applicationUpdateService.updateDeclaration(
                applicationId, true, LocalDate.of(2026, 8, 14), "123456/123"))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString())
        .cause()
        .isSameAs(datastoreFailure);

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    verify(mockApplicationGateway, times(2)).editApplication(eq(applicationId), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationConflictException_whenConflictPersistsAfterRetry() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(applicationWithSubmissionData(1L));
    ApplicationConflictException datastoreFailure =
        new ApplicationConflictException(
            "Application %s was modified concurrently".formatted(applicationId));
    doThrow(datastoreFailure)
        .when(mockApplicationGateway)
        .updateApplication(eq(applicationId), any());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString())
        .cause()
        .isSameAs(datastoreFailure);

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    verify(mockApplicationGateway, times(2)).updateApplication(eq(applicationId), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationConflictException_whenApplicationAlreadyRecorded() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED)
                .build());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateApplication(any(), any());
  }

  @Test
  void shouldUpdateDeclaration_throwsApplicationConflictException_whenApplicationAlreadyRecorded() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED)
                .build());

    assertThatThrownBy(() ->
            applicationUpdateService.updateDeclaration(
                applicationId, true, LocalDate.of(2026, 8, 14), "123456/123"))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).editApplication(any(), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationForbiddenException_whenOfficeCodeNotAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode("OTHER-OFFICE")
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateApplication(any(), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationForbiddenException_whenNoOfficesAreAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes()).thenReturn(List.of());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateApplication(any(), any());
  }

  private static ApplicationResponse applicationWithSubmissionData(long version) {
    return ApplicationResponse.builder()
        .eTag(version)
        .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
        .applicationState(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
        .client(new ClientDetails())
        .scopingQuestions(Map.of("familyLawClassification", "private"))
        .meansAssessmentRequired(true)
        .eligibilityAssessment(EligibilityAssessment.builder().data(new EligibilityData()).build())
        .evidence(new EvidenceResponse())
        .declaration(new DeclarationResponse())
        .build();
  }
}
