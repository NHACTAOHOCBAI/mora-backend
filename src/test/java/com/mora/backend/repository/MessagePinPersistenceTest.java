package com.mora.backend.repository;

import com.mora.backend.model.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
class MessagePinPersistenceTest {
    @Autowired EntityManager em;
    @Autowired MessagePinRepository pins;
    @Autowired ChatMessageRepository messages;
    @Autowired SpaceRepository spaces;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void pinUsesMessageIdAndCascadesWhenHistoryOrWorkspaceIsDeleted() {
        var user = User.builder().email("pin@test.example").password("unused")
                .fullName("Pin Test").role(Role.ROLE_USER).build();
        em.persist(user);
        var space = Space.builder().name("Test").user(user).build();
        em.persist(space);
        var message = ChatMessage.builder().space(space).sender("assistant").text("Knowledge").build();
        em.persist(message);
        var pin = MessagePin.builder().message(message).pinnedByUserId(user.getId())
                .pinnedByName(user.getFullName()).pinnedAt(LocalDateTime.now()).build();
        pins.saveAndFlush(pin);
        assertEquals(message.getId(), pin.getMessageId());
        em.clear();
        assertEquals("Knowledge", pins.findAllInSpace(space.getId()).getFirst().getMessage().getText());
        // Bulk SQL deletion verifies the actual ON DELETE CASCADE, not JPA cascade.
        em.createQuery("DELETE FROM ChatMessage m WHERE m.space.id = :id")
                .setParameter("id", space.getId()).executeUpdate();
        em.clear();
        assertEquals(0, pins.count());
        spaces.deleteById(space.getId());
        spaces.flush();
        assertFalse(spaces.existsById(space.getId()));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentPinsProduceOneRecordAndPreserveFirstAuthor() throws Exception {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        Long[] ids = tx.execute(status -> {
            var user = User.builder().email("concurrent@pin.example")
                    .password("unused").fullName("Concurrent Pin").role(Role.ROLE_USER).build();
            em.persist(user);
            var space = Space.builder().name("Concurrent").user(user).build();
            em.persist(space);
            var message = ChatMessage.builder().space(space).sender("assistant").text("Concurrent answer").build();
            em.persist(message);
            return new Long[]{user.getId(), space.getId(), message.getId()};
        });
        var users = org.mockito.Mockito.mock(com.mora.backend.service.UserService.class);
        org.mockito.Mockito.when(users.getCurrentUser()).thenReturn(
                User.builder().id(ids[0]).fullName("Concurrent Pin").role(Role.ROLE_USER).build());
        var notifier = org.mockito.Mockito.mock(com.mora.backend.service.ChatStateNotifier.class);
        var service = new com.mora.backend.service.impl.MessagePinServiceImpl(
                spaces, null, messages, pins, users, notifier);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Void> task = () -> {
                ready.countDown();
                assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                tx.executeWithoutResult(status -> service.pin(ids[1], ids[2]));
                return null;
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                assertEquals(1, pins.findAllInSpace(ids[1]).size());
                assertEquals(ids[0], pins.findById(ids[2]).orElseThrow().getPinnedByUserId());
            });
            org.mockito.Mockito.verify(notifier).changed(ids[1], false);
        } finally {
            tx.executeWithoutResult(status -> {
                messages.deleteBySpaceId(ids[1]);
                em.flush();
                spaces.deleteById(ids[1]);
                em.flush();
                em.remove(em.find(User.class, ids[0]));
            });
        }
    }
}
