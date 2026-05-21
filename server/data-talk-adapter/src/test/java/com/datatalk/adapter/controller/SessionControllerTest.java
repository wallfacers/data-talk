package com.datatalk.adapter.controller;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionControllerTest {

    @Test
    void deleteAll_returns204_and_calls_service() {
        SessionService service = mock(SessionService.class);
        Translator translator = mock(Translator.class);
        SessionController controller = new SessionController(service, translator);

        var response = controller.deleteAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteAll();
    }
}
