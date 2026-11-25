-- =====================================================================================
--  V24: RUC y razón social del dueño. En Perú, para vender hay que emitir comprobante, y
--  para eso hace falta RUC. El dueño puede crear y armar su tienda con su DNI, pero la tienda
--  no recibe pedidos hasta que registre su RUC y su razón social (en Mi perfil).
--
--  Validación en tres capas (página, servidor y base), con la fórmula oficial:
--    RUC (Perú, SUNAT): 11 dígitos, empieza con 10 (persona), 15 o 17 (no domiciliados) o 20
--      (empresa). El último dígito es el verificador: módulo 11 con pesos 5,4,3,2,7,6,5,4,3,2.
--      Un RUC 10 de una persona con DNI lleva su DNI adentro: 10 + DNI + verificador.
--    RUT (Chile, SII): 12345678-5. Verificador módulo 11 con pesos 2..7 desde la derecha (K = 10).
--  En Chile el RUT con que se registra el dueño ya es su identificación tributaria.
-- =====================================================================================

-- ---------------------------------------------------------------- dígitos verificadores

CREATE OR REPLACE FUNCTION fn_ruc_valido(p_ruc TEXT) RETURNS BOOLEAN AS $$
DECLARE
    v_pesos INT[] := ARRAY[5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
    v_suma  INT := 0;
    v_resto INT;
BEGIN
    IF p_ruc IS NULL OR p_ruc !~ '^(10|15|17|20)[0-9]{9}$' THEN
        RETURN FALSE;
    END IF;
    FOR i IN 1..10 LOOP
        v_suma := v_suma + substr(p_ruc, i, 1)::INT * v_pesos[i];
    END LOOP;
    v_resto := 11 - (v_suma % 11);
    IF v_resto = 10 THEN v_resto := 0; ELSIF v_resto = 11 THEN v_resto := 1; END IF;
    RETURN substr(p_ruc, 11, 1)::INT = v_resto;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION fn_rut_valido(p_rut TEXT) RETURNS BOOLEAN AS $$
DECLARE
    v_cuerpo TEXT;
    v_suma   INT := 0;
    v_peso   INT := 2;
    v_resto  INT;
    v_dv     TEXT;
BEGIN
    IF p_rut IS NULL OR p_rut !~ '^[0-9]{7,8}-[0-9K]$' THEN
        RETURN FALSE;
    END IF;
    v_cuerpo := split_part(p_rut, '-', 1);
    FOR i IN REVERSE length(v_cuerpo)..1 LOOP
        v_suma := v_suma + substr(v_cuerpo, i, 1)::INT * v_peso;
        v_peso := CASE WHEN v_peso = 7 THEN 2 ELSE v_peso + 1 END;
    END LOOP;
    v_resto := 11 - (v_suma % 11);
    v_dv := CASE v_resto WHEN 11 THEN '0' WHEN 10 THEN 'K' ELSE v_resto::TEXT END;
    RETURN split_part(p_rut, '-', 2) = v_dv;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ---------------------------------------------------------------- el RUC del dueño

ALTER TABLE propietarios ADD COLUMN IF NOT EXISTS ruc VARCHAR(11) NULL;

-- El dueño de la tienda de demostración (moda-lima, datos de prueba de migration-local/V3) tenía un
-- RUC inventado que no pasa el dígito verificador: se corrige al RUC válido más parecido.
UPDATE propietarios SET numero_documento = '20601234565'
 WHERE id_propietario = 9001 AND tipo_documento = 2 AND numero_documento = '20601234567';

-- Los dueños que se registraron con un RUC válido ya lo tienen. Su razón social, si no la
-- escribieron, es su nombre completo (lo que corresponde a una persona con RUC 10).
UPDATE propietarios p
   SET ruc = p.numero_documento,
       razon_social = COALESCE(NULLIF(trim(p.razon_social), ''),
                               trim(concat_ws(' ', l.nombres, l.primer_apellido, l.segundo_apellido)))
  FROM login l
 WHERE l.id_login = p.id_login
   AND p.tipo_documento = 2
   AND fn_ruc_valido(p.numero_documento);

ALTER TABLE propietarios ADD CONSTRAINT uq_propietarios_ruc UNIQUE (ruc);
-- Un RUC bien formado y con su dígito verificador correcto.
ALTER TABLE propietarios ADD CONSTRAINT ck_propietarios_ruc
    CHECK (ruc IS NULL OR fn_ruc_valido(ruc));
-- Con RUC, la razón social (nombre legal que sale en la boleta o factura) es obligatoria.
ALTER TABLE propietarios ADD CONSTRAINT ck_propietarios_ruc_razon
    CHECK (ruc IS NULL OR length(trim(coalesce(razon_social, ''))) >= 3);
-- Una persona con DNI que tiene RUC 10: su RUC lleva su DNI (10 + DNI + verificador).
ALTER TABLE propietarios ADD CONSTRAINT ck_propietarios_ruc_dni
    CHECK (ruc IS NULL OR tipo_documento <> 1 OR left(ruc, 2) <> '10' OR substr(ruc, 3, 8) = numero_documento);
-- Quien se registró con su RUC no puede declarar otro distinto.
ALTER TABLE propietarios ADD CONSTRAINT ck_propietarios_ruc_tipo
    CHECK (ruc IS NULL OR tipo_documento <> 2 OR ruc = numero_documento);

-- ---------------------------------------------------------------- documento con verificador

-- T13 ampliado: además del formato, el RUC y el RUT deben tener su dígito verificador correcto.
-- Solo al registrar o al cambiar el número: así un dueño antiguo puede seguir editando su perfil.
CREATE OR REPLACE FUNCTION fn_documento_valido() RETURNS trigger AS $$
DECLARE v_tipo RECORD;
BEGIN
    IF TG_TABLE_NAME = 'tipos_documento' THEN
        IF EXISTS (SELECT 1 FROM propietarios
                    WHERE tipo_documento = NEW.id_tipo_documento AND numero_documento !~ NEW.patron) THEN
            RAISE EXCEPTION 'Hay documentos % registrados que no cumplen el nuevo formato', NEW.codigo;
        END IF;
        IF NEW.id_pais IS DISTINCT FROM OLD.id_pais
           AND EXISTS (SELECT 1 FROM propietarios WHERE tipo_documento = OLD.id_tipo_documento) THEN
            RAISE EXCEPTION 'El tipo de documento % (%) ya lo usan dueños: su país no se cambia', OLD.codigo, OLD.id_pais;
        END IF;
        RETURN NEW;
    END IF;

    SELECT codigo, id_pais, patron INTO v_tipo FROM tipos_documento WHERE id_tipo_documento = NEW.tipo_documento;
    IF FOUND AND NEW.numero_documento !~ v_tipo.patron THEN
        RAISE EXCEPTION 'El número "%" no tiene el formato de % (%)', NEW.numero_documento, v_tipo.codigo, v_tipo.id_pais;
    END IF;
    IF TG_OP = 'INSERT' OR NEW.numero_documento IS DISTINCT FROM OLD.numero_documento THEN
        IF v_tipo.codigo = 'RUC' AND v_tipo.id_pais = 'PE' AND NOT fn_ruc_valido(NEW.numero_documento) THEN
            RAISE EXCEPTION 'El RUC % no es válido: su dígito verificador no coincide', NEW.numero_documento;
        END IF;
        IF v_tipo.codigo = 'RUT' AND NOT fn_rut_valido(NEW.numero_documento) THEN
            RAISE EXCEPTION 'El RUT % no es válido: su dígito verificador no coincide', NEW.numero_documento;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------- vender exige RUC

-- ¿La tienda ya puede vender? En Perú, solo si su dueño registró su RUC (y con él su razón
-- social). En Chile el dueño se registra con su RUT, que ya es su identificación tributaria.
CREATE OR REPLACE FUNCTION fn_tienda_puede_vender(p_tienda BIGINT) RETURNS BOOLEAN AS $$
    SELECT t.id_pais <> 'PE' OR p.ruc IS NOT NULL
      FROM tiendas t JOIN propietarios p ON p.id_propietario = t.id_propietario
     WHERE t.id_tienda = p_tienda;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION fn_pedido_requiere_ruc() RETURNS trigger AS $$
BEGIN
    IF NOT coalesce(fn_tienda_puede_vender(NEW.id_tienda), FALSE) THEN
        RAISE EXCEPTION 'Esta tienda todavía no puede vender: su dueño debe registrar su RUC y su razón social';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tg_pedidos_requiere_ruc ON pedidos;
CREATE TRIGGER tg_pedidos_requiere_ruc BEFORE INSERT ON pedidos
    FOR EACH ROW EXECUTE FUNCTION fn_pedido_requiere_ruc();
