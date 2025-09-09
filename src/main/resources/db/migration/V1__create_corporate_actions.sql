CREATE TABLE IF NOT EXISTS corporate_actions (
  id BIGSERIAL PRIMARY KEY,
  symbol VARCHAR(64) NOT NULL,
  action_date DATE NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  ratio NUMERIC(20,8) DEFAULT 0,
  price NUMERIC(20,8) DEFAULT 0,
  ex_date DATE,
  record_date DATE,
  payment_date DATE
);

CREATE INDEX IF NOT EXISTS idx_corp_actions_symbol_date ON corporate_actions(symbol, action_date);
CREATE INDEX IF NOT EXISTS idx_corp_actions_type ON corporate_actions(action_type);

