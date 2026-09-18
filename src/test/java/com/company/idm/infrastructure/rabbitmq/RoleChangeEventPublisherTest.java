package com.company.idm.infrastructure.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.rolegroup.RoleMembershipChangedEvent;
import com.company.idm.domain.rolegroup.RoleSupplyEventType;
import com.company.idm.infrastructure.config.RabbitmqProperties;
import com.company.idm.infrastructure.config.RoleSupplyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * 事件发布器测试：验证保序投递、确认失败停止、序列化失败留待重试，以及范围分层路由键。
 */
class RoleChangeEventPublisherTest {

    private final RoleMembershipChangeRepository changeRepository = mock(RoleMembershipChangeRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitmqProperties properties = new RabbitmqProperties();
    private final RoleSupplyProperties supplyProperties = new RoleSupplyProperties();
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final RoleChangeEventPublisher publisher = new RoleChangeEventPublisher(
        changeRepository,
        rabbitTemplate,
        properties,
        supplyProperties,
        objectMapper
    );

    @Test
    void acknowledgedBacklogIsMarkedPublishedInIdOrderWithConsistentMessageMetadata() throws Exception {
        when(changeRepository.findUnpublished(200))
            .thenReturn(List.of(change(1L, "finance.reader"), change(2L, "audit.reader")), List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":1}");
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("idm.role-change"), anyString(), any(Message.class), any(CorrelationData.class));

        publisher.publishPendingChanges();

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, times(2)).send(
            eq("idm.role-change"), routingKeyCaptor.capture(), messageCaptor.capture(), any(CorrelationData.class)
        );
        assertThat(routingKeyCaptor.getAllValues())
            .containsExactly("rg.10.role.finance.reader", "rg.10.role.audit.reader");
        assertThat(messageCaptor.getAllValues())
            .allSatisfy(message -> {
                assertThat(message.getMessageProperties().getContentType())
                    .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
                assertThat(message.getMessageProperties().getHeaders())
                    .containsEntry("x-idm-message-type", RoleSupplyMessage.TYPE_ROLE_CHANGE)
                    .containsEntry("x-idm-protocol-version", 2);
            });
        assertThat(messageCaptor.getAllValues().get(0).getMessageProperties().getMessageId()).isEqualTo("1");
        assertThat(messageCaptor.getAllValues().get(1).getMessageProperties().getMessageId()).isEqualTo("2");

        assertMarkedPublished(1L, 2L);
    }

    @Test
    void groupAndSubscriptionControlEventsUseScopeSpecificRoutingKeys() throws Exception {
        when(changeRepository.findUnpublished(200)).thenReturn(
            List.of(
                event(3L, RoleSupplyEventType.GROUP_UPDATED, null, 10L, null),
                event(4L, RoleSupplyEventType.SUBSCRIPTION_CONTROL, null, 10L, 6L),
                event(5L, RoleSupplyEventType.MEMBER_ADDED, "GLOBAL_USER", null, null)
            ),
            List.of()
        );
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("idm.role-change"), anyString(), any(Message.class), any(CorrelationData.class));

        publisher.publishPendingChanges();

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(3)).send(
            eq("idm.role-change"), routingKeyCaptor.capture(), any(Message.class), any(CorrelationData.class)
        );
        assertThat(routingKeyCaptor.getAllValues()).containsExactly(
            "rg.10.group",
            "rg.10.subscription",
            "rg.0.role.GLOBAL_USER"
        );
    }

    @Test
    void failedConfirmKeepsOrderAndStopsBeforePublishingFollowingEvents() throws Exception {
        when(changeRepository.findUnpublished(200))
            .thenReturn(List.of(change(1L, "finance.reader"), change(2L, "audit.reader")));
        when(objectMapper.writeValueAsString(any())).thenAnswer(invocation -> "{}");
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            boolean ack = attempts.getAndIncrement() == 0;
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).send(eq("idm.role-change"), anyString(), any(Message.class), any(CorrelationData.class));

        publisher.publishPendingChanges();

        verify(rabbitTemplate, times(2)).send(
            eq("idm.role-change"), anyString(), any(Message.class), any(CorrelationData.class)
        );
        assertMarkedPublished(1L);
    }

    @Test
    void sendFailuresLeaveEventsUnpublishedForTheNextCycle() throws Exception {
        when(changeRepository.findUnpublished(200)).thenReturn(List.of(change(1L, "finance.reader")));
        when(objectMapper.writeValueAsString(any())).thenAnswer(invocation -> {
            throw new IllegalStateException("serialization failed");
        });

        publisher.publishPendingChanges();

        verify(rabbitTemplate, never()).send(
            anyString(), anyString(), any(Message.class), any(CorrelationData.class)
        );
        verify(changeRepository, never()).markPublished(any(), any(LocalDateTime.class));
    }

    @Test
    void messagePayloadCarriesPlatformUserIdScopeVersionAndEventType() throws Exception {
        RoleSupplyMessage message = RoleSupplyMessage.from(
            change(1L, "finance.reader"), 2, "corp-idm", new ObjectMapper()
        );

        assertThat(message.userId()).isEqualTo("zhangsan-id");
        assertThat(message.eventType()).isEqualTo("MEMBER_ADDED");
        assertThat(message.scopeVersion()).isEqualTo(4L);
        assertThat(message.sourceId()).isEqualTo("corp-idm");
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(message);
        assertThat(payload).contains("\"userId\":\"zhangsan-id\"");
    }

    @Test
    void disabledPublisherSkipsAllWork() {
        properties.getPublisher().setEnabled(false);

        publisher.publishPendingChanges();

        verifyNoInteractions(changeRepository, rabbitTemplate);
    }

    @Test
    void membershipChangedEventTriggersTheSamePublishFlow() {
        when(changeRepository.findUnpublished(200)).thenReturn(List.of());

        publisher.onRoleMembershipChanged(new RoleMembershipChangedEvent(1L));

        // 事务提交后推送已改为异步触发，需等待发布线程完成后再校验。
        verify(changeRepository, timeout(2000)).findUnpublished(200);
    }

    private void assertMarkedPublished(Long... expectedIds) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(changeRepository).markPublished(idsCaptor.capture(), any(LocalDateTime.class));
        assertThat(idsCaptor.getValue()).containsExactly(expectedIds);
    }

    private RoleMembershipChange change(Long id, String roleCode) {
        return event(id, RoleSupplyEventType.MEMBER_ADDED, roleCode, 10L, null);
    }

    private RoleMembershipChange event(
        Long id,
        RoleSupplyEventType eventType,
        String roleCode,
        Long roleGroupId,
        Long controlVersion
    ) {
        return new RoleMembershipChange(
            id,
            eventType,
            roleCode == null ? null : 20L + id,
            roleCode,
            roleCode,
            roleGroupId == null ? RoleScope.GLOBAL : RoleScope.GROUP,
            roleGroupId,
            roleGroupId == null ? null : 4L,
            7L,
            "zhangsan",
            "zhangsan-id",
            eventType == RoleSupplyEventType.MEMBER_ADDED ? "ADDED" : null,
            null,
            controlVersion == null ? null : 4L,
            controlVersion,
            LocalDateTime.now()
        );
    }
}
