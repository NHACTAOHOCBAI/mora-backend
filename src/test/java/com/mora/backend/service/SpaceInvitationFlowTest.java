package com.mora.backend.service;

import com.mora.backend.exception.*;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.impl.SpaceServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.show-sql=false"
})
@Import(SpaceServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SpaceInvitationFlowTest {
    @Autowired SpaceService service;
    @Autowired SpaceRepository spaces;
    @Autowired SpaceMemberRepository members;
    @Autowired SpaceInvitationRepository invitations;
    @Autowired UserRepository accounts;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean UserService users;
    @MockitoBean DocumentService documents;
    final ThreadLocal<User> actor = new ThreadLocal<>();
    User owner, guest;
    Long spaceId, invitationId;
    String code;
    TransactionTemplate tx;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        code = UUID.randomUUID().toString().toUpperCase();
        tx.executeWithoutResult(status -> {
            owner = accounts.saveAndFlush(User.builder().email(code + "owner@test.example")
                    .password("unused").fullName("Owner").role(Role.ROLE_USER).build());
            guest = accounts.saveAndFlush(User.builder().email(code + "guest@test.example")
                    .password("unused").fullName("Guest").role(Role.ROLE_USER).build());
            var space = spaces.saveAndFlush(Space.builder().name("Invitation test").user(owner).build());
            spaceId = space.getId();
            invitationId = invitations.saveAndFlush(SpaceInvitation.builder().space(space).inviter(owner)
                    .inviteCode(code).role(SpaceRole.EDITOR).expiresAt(LocalDateTime.now().plusDays(1)).build()).getId();
        });
        actor.set(guest);
        when(users.getCurrentUser()).thenAnswer(invocation -> actor.get());
    }

    @Test void ownerIsAlreadyMemberWithoutCreatingMembership() {
        actor.set(owner);
        assertTrue(service.previewInvitation(code).isAlreadyMember());
        assertEquals(SpaceRole.OWNER, service.joinSpaceByInviteCode(code).getCurrentUserRole());
        assertFalse(members.existsBySpaceIdAndUserId(spaceId, owner.getId()));
    }

    @Test void previewUsesTheMembershipPropertyExpectedByTheFrontend() throws Exception {
        actor.set(owner);
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(service.previewInvitation(code));
        assertTrue(json.contains("\"isAlreadyMember\":true"));
    }

    @Test void repeatedJoinKeepsExistingRoleAndExpiredInvitationIsRejected() {
        assertEquals(SpaceRole.EDITOR, service.joinSpaceByInviteCode(code).getCurrentUserRole());
        tx.executeWithoutResult(status -> invitations.findById(invitationId).orElseThrow().setRole(SpaceRole.VIEWER));
        assertEquals(SpaceRole.EDITOR, service.joinSpaceByInviteCode(code).getCurrentUserRole());
        assertEquals(1, members.findBySpaceId(spaceId).size());
        tx.executeWithoutResult(status -> invitations.findById(invitationId).orElseThrow().setExpiresAt(LocalDateTime.now().minusSeconds(1)));
        assertEquals(ErrorCode.INVITATION_EXPIRED, assertThrows(AppException.class,
                () -> service.joinSpaceByInviteCode(code)).getErrorCode());
    }

    @Test void concurrentJoinCreatesOnlyOneMembership() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Long> join = () -> {
                actor.set(guest);
                start.await();
                return service.joinSpaceByInviteCode(code).getId();
            };
            var first = pool.submit(join);
            var second = pool.submit(join);
            start.countDown();
            assertEquals(spaceId, first.get(10, TimeUnit.SECONDS));
            assertEquals(spaceId, second.get(10, TimeUnit.SECONDS));
            assertEquals(1, members.findBySpaceId(spaceId).size());
        }
    }

    @Test void joinReadsRevocationCommittedWhileWaitingForWorkspaceLock() throws Exception {
        var started = new CountDownLatch(1);
        actor.set(owner);
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?> future = tx.execute(status -> {
                spaces.findForUpdate(spaceId).orElseThrow();
                var joining = pool.submit(() -> {
                    actor.set(guest);
                    started.countDown();
                    service.joinSpaceByInviteCode(code);
                });
                try { assertTrue(started.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new AssertionError(error); }
                service.revokeInvitation(spaceId, invitationId);
                return joining;
            });
            var error = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
            assertInstanceOf(AppException.class, error.getCause());
            assertFalse(members.existsBySpaceIdAndUserId(spaceId, guest.getId()));
        }
    }

    @Test void cannotRevokeAnInvitationUsingAnotherWorkspaceId() {
        actor.set(owner);
        Long other = tx.execute(status -> spaces.saveAndFlush(Space.builder().name("Other").user(owner).build()).getId());
        assertEquals(ErrorCode.INVITATION_NOT_FOUND, assertThrows(AppException.class,
                () -> service.revokeInvitation(other, invitationId)).getErrorCode());
        assertEquals(InvitationStatus.PENDING, invitations.findById(invitationId).orElseThrow().getStatus());
    }
}
