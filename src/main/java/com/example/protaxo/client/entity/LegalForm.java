package com.example.protaxo.client.entity;

/**
 * Організаційно-правова форма контрагента (V78). Обирається в списку ліворуч від назви; з неї й назви
 * складаються коротка назва («ТОВ «Агро»») і повна («Товариство з обмеженою відповідальністю «Агро»»).
 * ФОП — фізособа: назва без лапок (ПІБ), а код ЄДРПОУ — це його РНОКПП (ІПН), 10 цифр.
 */
public enum LegalForm {
    TOV("ТОВ", "Товариство з обмеженою відповідальністю"),
    /** Те саме ТОВ, але так скорочують на заході України («ТзОВ «Агро»»). */
    TZOV("ТзОВ", "Товариство з обмеженою відповідальністю"),
    FOP("ФОП", "Фізична особа-підприємець"),
    PP("ПП", "Приватне підприємство"),
    AT("АТ", "Акціонерне товариство"),
    PRAT("ПрАТ", "Приватне акціонерне товариство"),
    PAT("ПАТ", "Публічне акціонерне товариство"),
    TDV("ТДВ", "Товариство з додатковою відповідальністю"),
    STOV("СТОВ", "Сільськогосподарське товариство з обмеженою відповідальністю"),
    DP("ДП", "Державне підприємство"),
    KP("КП", "Комунальне підприємство"),
    FG("ФГ", "Фермерське господарство"),
    SK("СК", "Сільськогосподарський кооператив"),
    GO("ГО", "Громадська організація"),
    BO("БО", "Благодійна організація"),
    OSBB("ОСББ", "Об'єднання співвласників багатоквартирного будинку"),
    /** Без префікса — назва як є. */
    OTHER("", ""),
    /**
     * Своя форма з довідника {@link CustomLegalForm} (V81): скорочення й повна назва — там, тож методи
     * нижче без них поводяться як OTHER; складає назву контролер через {@link #withLabel}.
     */
    CUSTOM("", "");

    private final String shortLabel;
    private final String fullLabel;

    LegalForm(String shortLabel, String fullLabel) {
        this.shortLabel = shortLabel;
        this.fullLabel = fullLabel;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public String getFullLabel() {
        return fullLabel;
    }

    public boolean isIndividual() {
        return this == FOP;
    }

    /** «ТОВ «Агро»», «ФОП Іваненко Петро Васильович»; для OTHER — назва як є (лапки не чіпаємо). */
    public String shortName(String name) {
        if (shortLabel.isEmpty()) {
            return name == null ? "" : name.trim();
        }
        return withLabel(shortLabel, name, isIndividual());
    }

    /** «Товариство з обмеженою відповідальністю «Агро»». */
    public String fullName(String name) {
        if (fullLabel.isEmpty()) {
            return name == null ? "" : name.trim();
        }
        return withLabel(fullLabel, name, isIndividual());
    }

    /** Назва без форми власності й лапок — для поля праворуч від списку при редагуванні. */
    public String stripFrom(String composedName) {
        if (shortLabel.isEmpty()) {
            return composedName == null ? "" : bare(composedName);
        }
        return stripLabel(shortLabel, composedName);
    }

    /** «Мітка «Назва»» (юрособа) або «Мітка Назва» (фізособа); порожня назва — порожньо. */
    public static String withLabel(String label, String name, boolean individual) {
        String bare = bare(name);
        if (bare.isEmpty()) {
            return bare;
        }
        return individual ? label + " " + bare : label + " «" + bare + "»";
    }

    /** Зворотне до {@link #withLabel}: «ТОВ «Агро»» → Агро. */
    public static String stripLabel(String label, String composedName) {
        if (composedName == null) {
            return "";
        }
        String s = composedName.trim();
        if (s.startsWith(label + " ")) {
            s = s.substring(label.length() + 1);
        }
        return bare(s);
    }

    /** Без зайвих пробілів і без лапок по краях («Агро», "Агро" → Агро). */
    public static String bare(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim().replaceAll("\\s+", " ");
        while (!s.isEmpty() && "«\"'“„".indexOf(s.charAt(0)) >= 0) {
            s = s.substring(1).trim();
        }
        while (!s.isEmpty() && "»\"'”“".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }
}
