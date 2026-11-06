package com.paycore.financialcore.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Regras de criação e autorização por proprietário, sem infraestrutura (RF-02). */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

  @Mock private AccountRepository accounts;

  @InjectMocks private AccountService service;

  @Test
  void rejectsCurrencyThatIsNotUppercaseIso4217() {
    assertThatThrownBy(
            () ->
                service.create(
                    UUID.randomUUID(), "brl", Account.OverdraftPolicy.FORBIDDEN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currency");
    verifyNoInteractions(accounts);
  }

  @Test
  void createsAccountWithOwnerFromTheCaller() {
    UUID ownerId = UUID.randomUUID();
    when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Account account = service.create(ownerId, "BRL", Account.OverdraftPolicy.ALLOWED);

    assertThat(account.getOwnerId()).isEqualTo(ownerId);
    assertThat(account.getCurrency()).isEqualTo("BRL");
    assertThat(account.getOverdraftPolicy()).isEqualTo(Account.OverdraftPolicy.ALLOWED);
    assertThat(account.getStatus()).isEqualTo(Account.Status.ACTIVE);
    verify(accounts).save(account);
  }

  @Test
  void returnsAccountForItsOwner() {
    UUID ownerId = UUID.randomUUID();
    Account account =
        new Account(UUID.randomUUID(), ownerId, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

    assertThat(service.requireOwned(account.getId(), ownerId)).isSameAs(account);
  }

  @Test
  void hidesAccountsOfOtherOwnersBehindAccessDenied() {
    Account account =
        new Account(
            UUID.randomUUID(), UUID.randomUUID(), "BRL", Account.OverdraftPolicy.FORBIDDEN);
    when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> service.requireOwned(account.getId(), UUID.randomUUID()))
        .isInstanceOf(AccountAccessDeniedException.class);
  }

  @Test
  void reportsMissingAccount() {
    UUID accountId = UUID.randomUUID();
    when(accounts.findById(accountId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireOwned(accountId, UUID.randomUUID()))
        .isInstanceOf(AccountNotFoundException.class);
  }
}
