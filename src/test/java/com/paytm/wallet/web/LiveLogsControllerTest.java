package com.paytm.wallet.web;

import com.paytm.wallet.observability.LiveLogBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class LiveLogsControllerTest {

    private LiveLogBuffer buffer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        buffer = new LiveLogBuffer(50);
        mockMvc = standaloneSetup(new LiveLogsController(buffer)).build();
    }

    @Test
    void logsPage_returnsHtmlWithEventSource() throws Exception {
        mockMvc.perform(get("/logs"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("/logs/stream")));
    }

    @Test
    void logsStream_startsServerSentEvents() throws Exception {
        buffer.offer("{\"message\":\"event=transfer_completed\"}");

        MvcResult result = mockMvc.perform(get("/logs/stream").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andReturn();

        assertThat(result.getRequest().isAsyncStarted()).isTrue();
    }
}
