package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MessageRepositoryIT {

    @Autowired MessageRepository msgRepo;
    @Autowired SessionRepository sessRepo;
    @Autowired JdbcTemplate datatalkJdbc;

    @Test
    void savesMessageWithPartsAndListsByCreatedAt() {
        String sid = "sess-msg-" + System.nanoTime();
        sessRepo.upsert(new SessionRecord(sid, null, "T", false, null, 100L, 100L));
        TextPart p1 = new TextPart("p-msg", sid, "m-msg", "hello", false, false, null, Map.of());
        Message m = new Message("m-msg", sid, Message.Role.USER, List.of(p1), 101L);
        msgRepo.save(m);

        List<Message> all = msgRepo.findBySession(sid);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).parts()).hasSize(1);
        assertThat(((TextPart) all.get(0).parts().get(0)).text()).isEqualTo("hello");
    }
}
