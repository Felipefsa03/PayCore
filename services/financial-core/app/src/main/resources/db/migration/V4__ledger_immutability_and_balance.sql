-- Imutabilidade e balanceamento do ledger (card M2: ledger de partidas dobradas).
-- O balanceamento por moeda é verificado no commit da transação, não por linha,
-- porque depende da agregação de todos os postings do journal entry.

CREATE OR REPLACE FUNCTION paycore_reject_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'ledger entries are immutable: % on %', TG_OP, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_journal_entries_immutable
  BEFORE UPDATE OR DELETE ON journal_entries
  FOR EACH ROW EXECUTE FUNCTION paycore_reject_ledger_mutation();

CREATE TRIGGER trg_postings_immutable
  BEFORE UPDATE OR DELETE ON postings
  FOR EACH ROW EXECUTE FUNCTION paycore_reject_ledger_mutation();

CREATE OR REPLACE FUNCTION paycore_check_posting_currency()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  entry_currency varchar(3);
BEGIN
  SELECT currency INTO entry_currency
    FROM journal_entries
   WHERE id = NEW.journal_entry_id;
  IF entry_currency IS NULL THEN
    RAISE EXCEPTION 'journal entry % does not exist', NEW.journal_entry_id;
  END IF;
  IF NEW.currency <> entry_currency THEN
    RAISE EXCEPTION 'posting currency (%) must match journal entry currency (%)',
      NEW.currency, entry_currency;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_posting_currency
  BEFORE INSERT OR UPDATE ON postings
  FOR EACH ROW EXECUTE FUNCTION paycore_check_posting_currency();

CREATE OR REPLACE FUNCTION paycore_check_journal_balanced()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  affected_journal uuid;
  net bigint;
  posting_count integer;
BEGIN
  IF TG_TABLE_NAME = 'journal_entries' THEN
    affected_journal := COALESCE(NEW.id, OLD.id);
  ELSE
    affected_journal := COALESCE(NEW.journal_entry_id, OLD.journal_entry_id);
  END IF;

  SELECT COUNT(*),
         COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0)
    INTO posting_count, net
    FROM postings
   WHERE journal_entry_id = affected_journal;

  IF posting_count = 0 THEN
    RAISE EXCEPTION 'journal entry % must contain at least one posting', affected_journal;
  END IF;
  IF net <> 0 THEN
    RAISE EXCEPTION 'journal entry % is not balanced: net=% (must be 0)', affected_journal, net;
  END IF;
  RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE CONSTRAINT TRIGGER trg_journal_entry_balanced
  AFTER INSERT OR UPDATE OR DELETE ON postings
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION paycore_check_journal_balanced();

-- A verificacao de ao menos um posting e garantida pela aplicacao; journals
-- sem postings nao sao rejeitados no nivel do banco para permitir insercoes em
-- multiplos statements auto-commitados (ex.: testes de integracao).

CREATE INDEX idx_postings_account_created_at ON postings (account_id, created_at DESC);
