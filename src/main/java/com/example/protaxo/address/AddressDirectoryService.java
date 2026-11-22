package com.example.protaxo.address;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Довідник адрес КАТОТТГ (V73, docs/Контрагенти.md): регіони, пошук населених пунктів і складання
 * юридичної адреси «45604, Волинська обл., Луцький р-н, с. Крупа, вул. Дубнівська, 10».
 * Лише читання — дані змінюються новою міграцією з оновленого кодифікатора.
 */
@Service
@RequiredArgsConstructor
public class AddressDirectoryService {

    private static final int SEARCH_LIMIT = 20;

    private final JdbcTemplate jdbcTemplate;

    public record Region(String code, String name, String kind) {
        /** «Волинська обл.», «Автономна Республіка Крим», «м. Київ». */
        public String label() {
            return regionLabel(name, kind);
        }
    }

    public record Settlement(String code, String kind, String name, String regionCode, String regionName,
                             String regionKind, String districtName, String hromadaName) {

        /** «с. Крупа», «м. Луцьк», «с-ще Торчин». */
        public String shortLabel() {
            return settlementPrefix(kind) + " " + name;
        }

        /** «с. Крупа, Луцький р-н, Волинська обл.»; для Києва/Севастополя — «м. Київ». */
        public String label() {
            if ("K".equals(kind)) {
                return shortLabel();
            }
            return Stream.of(shortLabel(), districtName == null ? null : districtName + " р-н",
                            regionLabel(regionName, regionKind))
                    .filter(Objects::nonNull).collect(Collectors.joining(", "));
        }
    }

    private static final RowMapper<Settlement> SETTLEMENT = (rs, i) -> new Settlement(
            rs.getString("code"), rs.getString("kind"), rs.getString("name"), rs.getString("region_code"),
            rs.getString("region_name"), rs.getString("region_kind"), rs.getString("district_name"),
            rs.getString("hromada_name"));

    private static final String SETTLEMENT_SELECT = """
            SELECT s.code, s.kind, s.name, s.region_code, r.name AS region_name, r.kind AS region_kind,
                   d.name AS district_name, s.hromada_name
            FROM address_settlements s
            JOIN address_regions r ON r.code = s.region_code
            LEFT JOIN address_districts d ON d.code = s.district_code
            """;

    public List<Region> regions() {
        return jdbcTemplate.query(
                "SELECT code, name, kind FROM address_regions ORDER BY kind = 'K', name",
                (rs, i) -> new Region(rs.getString("code"), rs.getString("name"), rs.getString("kind")));
    }

    public Optional<Settlement> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SETTLEMENT_SELECT + " WHERE s.code = ?", SETTLEMENT, code).stream().findFirst();
    }

    /**
     * Населені пункти, назва яких починається з {@code query} (без урахування регістру й виду
     * апострофа; «с.», «м.» на початку ігноруються). Міста першими, далі селища, села.
     */
    public List<Settlement> search(String query, String regionCode) {
        String q = normalizeQuery(query);
        if (q.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(SETTLEMENT_SELECT).append(" WHERE lower(s.name) LIKE ?");
        List<Object> args = new ArrayList<>();
        args.add(q.replace("%", "").replace("_", "") + "%");
        if (regionCode != null && !regionCode.isBlank()) {
            sql.append(" AND s.region_code = ?");
            args.add(regionCode);
        }
        sql.append(" ORDER BY CASE s.kind WHEN 'K' THEN 0 WHEN 'M' THEN 1 WHEN 'X' THEN 2 ELSE 3 END, s.name, r.name LIMIT ")
                .append(SEARCH_LIMIT);
        return jdbcTemplate.query(sql.toString(), SETTLEMENT, args.toArray());
    }

    /**
     * Індекси, які вже вводили для цих населених пунктів: код → індекси, найчастіший першим
     * (V74 — офіційного довідника індексів за КАТОТТГ у відкритому доступі немає).
     */
    public Map<String, List<String>> postalCodes(Collection<String> settlementCodes) {
        Map<String, List<String>> result = new HashMap<>();
        if (settlementCodes == null || settlementCodes.isEmpty()) {
            return result;
        }
        List<String> codes = List.copyOf(settlementCodes);
        String placeholders = String.join(",", Collections.nCopies(codes.size(), "?"));
        jdbcTemplate.query("SELECT settlement_code, postal_code FROM address_postal_codes WHERE settlement_code IN ("
                        + placeholders + ") ORDER BY uses DESC, last_used_at DESC, postal_code",
                rs -> {
                    result.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                }, codes.toArray());
        return result;
    }

    /** Запам'ятати індекс населеного пункту (зберегли контрагента з такою адресою). */
    @Transactional
    public void rememberPostalCode(String settlementCode, String postalCode) {
        if (settlementCode == null || postalCode == null || !postalCode.matches("\\d{5}")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO address_postal_codes (settlement_code, postal_code) VALUES (?, ?)
                ON CONFLICT (settlement_code, postal_code)
                DO UPDATE SET uses = address_postal_codes.uses + 1, last_used_at = now()
                """, settlementCode, postalCode);
    }

    /**
     * Повна юридична адреса з частин; порожні частини пропускаються. Регіон не дублюється для
     * Києва/Севастополя (там населений пункт і є регіон).
     */
    public String format(String postalCode, Settlement settlement, String street, String building, String office) {
        List<String> parts = new ArrayList<>();
        add(parts, postalCode);
        if (settlement != null) {
            if (!"K".equals(settlement.kind())) {
                add(parts, regionLabel(settlement.regionName(), settlement.regionKind()));
                add(parts, settlement.districtName() == null ? null : settlement.districtName() + " р-н");
            }
            add(parts, settlement.shortLabel());
        }
        add(parts, street);
        add(parts, building);
        add(parts, office);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    static String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        String q = query.trim().toLowerCase()
                .replace('\'', '’').replace('ʼ', '’').replace('`', '’')
                .replaceAll("\\s+", " ");
        return q.replaceFirst("^(м\\.|місто|с\\.|село|с-ще|смт|селище)\\s*", "").trim();
    }

    static String regionLabel(String name, String kind) {
        if ("K".equals(kind)) {
            return "м. " + name;
        }
        return name.startsWith("Автономна") ? name : name + " обл.";
    }

    static String settlementPrefix(String kind) {
        return switch (kind) {
            case "M", "K" -> "м.";
            case "X" -> "с-ще";
            default -> "с.";
        };
    }

    private static void add(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }
}
