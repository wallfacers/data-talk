package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MessageRepositoryIT {

    @Autowired MessageRepository msgRepo;
    @Autowired SessionRepository sessRepo;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate dtJdbc;

    @Test
    void savesMessageAndVerifiesJsonStored() {
        String sid = "sess-msg-" + System.nanoTime();
        String mid = "msg-" + System.nanoTime();
        sessRepo.upsert(new SessionRecord(sid, null, "T", false, null, 100L, 100L));
        TextPart p1 = new TextPart("p-msg", sid, mid, "hello", false, false, null, Map.of());
        Message m = new Message(mid, sid, Message.Role.USER, List.of(p1), 101L);
        msgRepo.save(m);

        String partsJson = dtJdbc.queryForObject(
            "SELECT parts_json FROM messages WHERE id = ?", String.class, mid);
        assertThat(partsJson).contains("hello");
    }
}
