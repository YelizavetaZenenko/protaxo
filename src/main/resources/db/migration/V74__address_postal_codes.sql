-- Поштові індекси, які вже вводили для населеного пункту (docs/Контрагенти.md). Відкритого
-- офіційного довідника індексів за КАТОТТГ немає (API Укрпошти — лише з ключем за договором),
-- тож система запам'ятовує індекс при збереженні контрагента й наступного разу підставляє
-- найчастіший. uses — скільки разів зберігали саме цю пару.
CREATE TABLE address_postal_codes (
    settlement_code VARCHAR(19) NOT NULL REFERENCES address_settlements (code),
    postal_code     VARCHAR(5)  NOT NULL,
    uses            INT         NOT NULL DEFAULT 1,
    last_used_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (settlement_code, postal_code)
);

-- Перший відомий індекс — адреса виконавця: 45604, Волинська обл., Луцький р-н, с. Крупа.
INSERT INTO address_postal_codes (settlement_code, postal_code)
SELECT code, '45604' FROM address_settlements WHERE code = 'UA07080230110067820';
