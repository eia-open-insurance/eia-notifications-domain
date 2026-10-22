package com.eia.notifications.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID TEMPLATE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPROVER = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @Mock
    private NotificationExpertClient client;

    @InjectMocks
    private NotificationTemplateService service;

    @Test
    void listsTemplatesWithNormalizedFilters() {
        NotificationTemplateDto template = template("DRAFT");
        when(client.findAllTemplates(TENANT, NotificationTemplateStatus.DRAFT, "CLAIM_NOTICE_STANDARD"))
                .thenReturn(List.of(template));

        assertThat(service.list(TENANT, " draft ", " CLAIM_NOTICE_STANDARD "))
                .containsExactly(template);
    }

    @Test
    void listsAllTemplatesWhenFiltersAreBlank() {
        when(client.findAllTemplates(TENANT, null, null)).thenReturn(List.of());

        assertThat(service.list(TENANT, " ", "  ")).isEmpty();
    }

    @Test
    void rejectsAnUnknownStatusFilter() {
        assertThatThrownBy(() -> service.list(TENANT, "PUBLISHED", null))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_TEMPLATE_STATUS));

        verifyNoInteractions(client);
    }

    @Test
    void createRejectsAMissingTemplateVersion() {
        assertThatThrownBy(() -> service.create(TENANT,
                new CreateNotificationTemplateCommand("CLAIM_NOTICE_STANDARD", null, "EMAIL", "en-US",
                        "Body")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_TEMPLATE_VERSION));

        verifyNoInteractions(client);
    }

    @Test
    void createRejectsANonPositiveTemplateVersion() {
        assertThatThrownBy(() -> service.create(TENANT,
                new CreateNotificationTemplateCommand("CLAIM_NOTICE_STANDARD", 0, "EMAIL", "en-US",
                        "Body")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_TEMPLATE_VERSION));

        verifyNoInteractions(client);
    }

    @Test
    void createRejectsAnUnknownChannel() {
        assertThatThrownBy(() -> service.create(TENANT,
                new CreateNotificationTemplateCommand("CLAIM_NOTICE_STANDARD", 1, "FAX", "en-US",
                        "Body")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_CHANNEL));

        verifyNoInteractions(client);
    }

    @Test
    void createDelegatesAValidTemplate() {
        CreateNotificationTemplateCommand command = new CreateNotificationTemplateCommand(
                "CLAIM_NOTICE_STANDARD", 1, "EMAIL", "en-US", "Body");
        NotificationTemplateDto template = template("DRAFT");
        when(client.createTemplate(TENANT, command)).thenReturn(template);

        assertThat(service.create(TENANT, command)).isEqualTo(template);
        verify(client).createTemplate(TENANT, command);
    }

    @Test
    void createAcceptsATemplateWithoutAChannel() {
        CreateNotificationTemplateCommand command = new CreateNotificationTemplateCommand(
                "CLAIM_NOTICE_STANDARD", 1, " ", "en-US", "Body");
        when(client.createTemplate(TENANT, command)).thenReturn(template("DRAFT"));

        assertThat(service.create(TENANT, command)).isNotNull();
    }

    @Test
    void approveRequiresAnApprover() {
        assertThatThrownBy(() -> service.approve(TENANT, TEMPLATE_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));

        verify(client, never()).approveTemplate(TENANT, TEMPLATE_ID, null);
    }

    @Test
    void approveDelegatesToTheExpert() {
        NotificationTemplateDto active = template("ACTIVE");
        when(client.approveTemplate(TENANT, TEMPLATE_ID, APPROVER)).thenReturn(active);

        assertThat(service.approve(TENANT, TEMPLATE_ID, APPROVER)).isEqualTo(active);
        verify(client).approveTemplate(TENANT, TEMPLATE_ID, APPROVER);
    }

    @Test
    void retireDelegatesToTheExpert() {
        NotificationTemplateDto retired = template("RETIRED");
        when(client.retireTemplate(TENANT, TEMPLATE_ID)).thenReturn(retired);

        assertThat(service.retire(TENANT, TEMPLATE_ID)).isEqualTo(retired);
        verify(client).retireTemplate(TENANT, TEMPLATE_ID);
    }

    private static NotificationTemplateDto template(String status) {
        Instant now = Instant.parse("2026-10-19T12:00:00Z");
        boolean approved = "ACTIVE".equals(status) || "RETIRED".equals(status);
        return new NotificationTemplateDto(TEMPLATE_ID, "CLAIM_NOTICE_STANDARD", 1, "EMAIL",
                "en-US", "Claim {{claimNumber}} requires attention.", status,
                approved ? APPROVER : null, approved ? now : null, now, now);
    }
}
