package com.banklab.fdservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.banklab.fdservice.client.ProductDetails;
import com.banklab.fdservice.client.ProductPricingClient;
import com.banklab.fdservice.entity.FdAccount;
import com.banklab.fdservice.entity.FdAccountRole;
import com.banklab.fdservice.entity.FdAccountSequence;
import com.banklab.fdservice.repository.FdAccountRepository;
import com.banklab.fdservice.repository.FdAccountRoleRepository;
import com.banklab.fdservice.repository.FdAccountSequenceRepository;

/**
 * End-to-end proof that POST /fd-accounts actually commits data: drives the real
 * controller -> service -> repository -> H2 stack. Only the outbound call to the
 * Product & Pricing service is stubbed (Group 2's service doesn't exist in this
 * test environment) — persistence itself goes through the real DB, and every
 * assertion below re-reads via a fresh repository call rather than trusting the
 * HTTP response body, per the requirement to prove the data was actually saved.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class FdAccountCreationIntegrationTest {

    private static final String BRANCH_CD = "001";
    private static final String EXPECTED_ACCT_NUM = "FD001000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FdAccountRepository accountRepository;

    @Autowired
    private FdAccountRoleRepository roleRepository;

    @Autowired
    private FdAccountSequenceRepository sequenceRepository;

    @MockitoBean
    private ProductPricingClient productPricingClient;

    @BeforeEach
    void setUp() {
        roleRepository.deleteAll();
        accountRepository.deleteAll();

        sequenceRepository.saveAndFlush(FdAccountSequence.builder()
                .branchCd(BRANCH_CD)
                .branchName("Head Office")
                .prefix("FD")
                .lastSeq(0L)
                .seqWidth(6)
                .build());

        when(productPricingClient.getProduct("FD-REG")).thenReturn(new ProductDetails(
                "FD-REG", "Regular Fixed Deposit", "USD", "QUARTERLY", "QUARTERLY", "PAYOUT"));
    }

    @Test
    void createAccountPersistsAccountAndRoleToDatabase() throws Exception {
        String requestBody = """
                {
                  "custId": "CUST-001",
                  "productCode": "FD-REG",
                  "principal": 50000.00,
                  "tenureMonths": 12,
                  "currencyCode": "USD",
                  "maturityInstruction": "PAYOUT",
                  "initialRoles": [
                    { "custId": "CUST-001", "roleType": "OWNER", "isPrimary": true }
                  ]
                }
                """;

        mockMvc.perform(post("/fd-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acctNum").value(EXPECTED_ACCT_NUM))
                .andExpect(jsonPath("$.custId").value("CUST-001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Re-fetch independently from the DB rather than trusting the HTTP response —
        // this is the actual proof that createAccount() committed real rows.
        Optional<FdAccount> persistedAccount = accountRepository.findByAcctNum(EXPECTED_ACCT_NUM);
        assertThat(persistedAccount).isPresent();

        FdAccount account = persistedAccount.get();
        assertThat(account.getCustId()).isEqualTo("CUST-001");
        assertThat(account.getPrdCode()).isEqualTo("FD-REG");
        assertThat(account.getPrincipalAmt()).isEqualByComparingTo("50000.00");
        assertThat(account.getPrincipalBal()).isEqualByComparingTo("50000.00");
        assertThat(account.getCcyCd()).isEqualTo("USD");
        assertThat(account.getCompoundFreq()).isEqualTo("QUARTERLY");
        assertThat(account.getPayoutFreq()).isEqualTo("QUARTERLY");
        assertThat(account.getSts()).isEqualTo("ACTIVE");
        assertThat(account.getTenureMonths()).isEqualTo(12);
        assertThat(account.getMatDt()).isEqualTo(account.getOpenDt().plusMonths(12));

        List<FdAccountRole> persistedRoles = roleRepository.findByFdaId(account.getFdaId());
        assertThat(persistedRoles).hasSize(1);
        assertThat(persistedRoles.get(0).getCustId()).isEqualTo("CUST-001");
        assertThat(persistedRoles.get(0).getRoleTyp()).isEqualTo("OWNER");
        assertThat(persistedRoles.get(0).getIsPrimary()).isTrue();

        FdAccountSequence updatedSequence = sequenceRepository.findById(BRANCH_CD).orElseThrow();
        assertThat(updatedSequence.getLastSeq()).isEqualTo(1L);
    }
}
