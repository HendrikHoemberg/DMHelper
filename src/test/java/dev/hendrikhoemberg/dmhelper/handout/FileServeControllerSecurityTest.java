package dev.hendrikhoemberg.dmhelper.handout;

import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.handout.web.FileServeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileServeController.class)
class FileServeControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HandoutService handoutService;

    private UUID handoutId;
    private Handout unpublished;

    @BeforeEach
    void setUp() {
        handoutId = UUID.randomUUID();
        unpublished = new Handout();
        unpublished.setId(handoutId);
        unpublished.setTitle("Secret Letter");
        unpublished.setFileName("letter.png");
        unpublished.setContentType("image/png");
        unpublished.setPresented(false);
        unpublished.setDmOnly(true);
    }

    @Test
    void playerFileEndpointReturns404ForUnpresentedHandout() throws Exception {
        when(handoutService.findById(handoutId)).thenReturn(unpublished);

        mockMvc.perform(get("/player/files/" + handoutId))
                .andExpect(status().isNotFound());
    }

    @Test
    void playerFileEndpointReturns200ForPresentedHandout() throws Exception {
        unpublished.setPresented(true);
        when(handoutService.findById(handoutId)).thenReturn(unpublished);
        when(handoutService.getFileContent(handoutId)).thenReturn(new byte[]{1,2,3});

        mockMvc.perform(get("/player/files/" + handoutId))
                .andExpect(status().isOk());
    }
}
