package com.jobtrack.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrack.support.IntegrationTest;
import com.jobtrack.support.TestApi;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class CsvIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    private TestApi newUser() throws Exception {
        return TestApi.register(mvc, json, "csv-" + UUID.randomUUID() + "@example.com");
    }

    private ResultActions upload(TestApi user, String csv, boolean dryRun) throws Exception {
        return user.perform(multipart("/api/v1/offers/import").file(
                new MockMultipartFile("file", "ofertas.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", Boolean.toString(dryRun)));
    }

    @Test
    void exportContainsOnlyOwnOffersAndDefusesFormulas() throws Exception {
        TestApi alice = newUser();
        TestApi bob = newUser();
        alice.createOffer("=HYPERLINK(\"http://evil\")", "Acme, S.L.", Map.of("skills", List.of("Java", "SQL"), "salaryMin", 0));
        bob.createOffer("Oferta de Bob", "Otra", Map.of());

        String csv = alice.get("/api/v1/offers/export").andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿title,company,");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"").contains("\"Acme, S.L.\"").contains("Java;SQL");
        assertThat(csv).doesNotContain("Bob");
    }

    @Test
    void exportedFileCanBeReimportedWithoutLosingData() throws Exception {
        TestApi alice = newUser();
        alice.createOffer("=SUM(1)", "Acme", Map.of("skills", List.of("Java"), "salaryMin", 0, "workMode", "REMOTE",
                "location", "Madrid", "detectedDate", "2026-01-15", "notes", "Línea 1\nLínea 2"));
        String csv = alice.get("/api/v1/offers/export").andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        TestApi bob = newUser();
        upload(bob, csv, false).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));

        bob.get("/api/v1/offers").andExpect(jsonPath("$.content[0].title").value("=SUM(1)"))
                .andExpect(jsonPath("$.content[0].salaryMin").value(0))
                .andExpect(jsonPath("$.content[0].salaryMax").value((Object) null))
                .andExpect(jsonPath("$.content[0].workMode").value("REMOTE"))
                .andExpect(jsonPath("$.content[0].detectedDate").value("2026-01-15"))
                .andExpect(jsonPath("$.content[0].notes").value("Línea 1\nLínea 2"))
                .andExpect(jsonPath("$.content[0].skills[0]").value("Java"));
    }

    @Test
    void dryRunValidatesWithoutSaving() throws Exception {
        TestApi user = newUser();
        upload(user, "title,company,status\nDev,Acme,APPLIED\nQA,Beta,\n", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.preview.length()").value(2));
        user.get("/api/v1/offers").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void importIsAllOrNothingAndReportsErrorsPerRow() throws Exception {
        TestApi user = newUser();
        String csv = """
                title,company,work_mode,salary_min,salary_max,applied_date,url
                Valid,Acme,REMOTE,10,20,2026-01-01,https://ok.example
                ,NoTitle,HYBRID,,,,
                Bad mode,Acme,MOON,,,,
                Bad money,Acme,,50,10,2026-01-01,javascript:alert(1)
                Bad date,Acme,,,,not-a-date,
                """;

        upload(user, csv, false)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.errors[?(@.row==3 && @.field=='title')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.row==4 && @.field=='work_mode')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.row==5 && @.field=='salary_min')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.row==5 && @.field=='url')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.row==6 && @.field=='applied_date')]").isNotEmpty());

        user.get("/api/v1/offers").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void malformedFilesAreRejectedCleanly() throws Exception {
        TestApi user = newUser();
        upload(user, "", true).andExpect(status().isBadRequest());
        upload(user, "foo,bar\n1,2\n", true).andExpect(status().isBadRequest());
        upload(user, "title,company\n", true).andExpect(status().isBadRequest());
        upload(user, "title,company\n\"unterminated,Acme\n", true).andExpect(status().isBadRequest());
        user.perform(multipart("/api/v1/offers/import").file(new MockMultipartFile("file", "x.csv", "text/csv", new byte[] {(byte) 0xff, (byte) 0xfe, 0x41})))
                .andExpect(status().isBadRequest());
        user.get("/api/v1/offers").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void importSetsInitialHistoryAndCompatibility() throws Exception {
        TestApi user = newUser();
        user.put("/api/v1/profile", Map.of("skills", List.of("Java"))).andExpect(status().isOk());
        upload(user, "title,company,skills,status\nDev,Acme,Java,APPLIED\n", false).andExpect(status().isOk());

        String id = com.jayway.jsonpath.JsonPath.read(
                user.get("/api/v1/offers").andReturn().getResponse().getContentAsString(), "$.content[0].id");
        user.get("/api/v1/offers/" + id).andExpect(jsonPath("$.compatibilityScore").value(100))
                .andExpect(jsonPath("$.status").value("APPLIED")).andExpect(jsonPath("$.appliedDate").isNotEmpty());
        user.get("/api/v1/offers/" + id + "/history").andExpect(jsonPath("$[0].toStatus").value("APPLIED"));
    }
}
