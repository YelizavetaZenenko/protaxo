package com.example.protaxo.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.protaxo.address.AddressDirectoryService.Settlement;
import org.junit.jupiter.api.Test;

/** Юридична адреса з КАТОТТГ: підписи населених пунктів і повний рядок для договору. */
class AddressDirectoryServiceTest {

    private final AddressDirectoryService service = new AddressDirectoryService(null);

    private static final Settlement KRUPA = new Settlement("UA07080230110067820", "C", "Крупа",
            "UA07000000000024379", "Волинська", "O", "Луцький", "Боратинська");
    private static final Settlement KYIV = new Settlement("UA80000000000093317", "K", "Київ",
            "UA80000000000093317", "Київ", "K", null, null);

    @Test
    void labelsShowDistrictAndRegion() {
        assertThat(KRUPA.shortLabel()).isEqualTo("с. Крупа");
        assertThat(KRUPA.label()).isEqualTo("с. Крупа, Луцький р-н, Волинська обл.");
        assertThat(KYIV.label()).isEqualTo("м. Київ");
    }

    @Test
    void formatsFullAddressInContractOrder() {
        assertThat(service.format("45604", KRUPA, "вул. Дубнівська", "10", null))
                .isEqualTo("45604, Волинська обл., Луцький р-н, с. Крупа, вул. Дубнівська, 10");
        assertThat(service.format("01001", KYIV, "вул. Хрещатик", "1", "оф. 5"))
                .isEqualTo("01001, м. Київ, вул. Хрещатик, 1, оф. 5");
        assertThat(service.format(null, null, null, null, null)).isNull();
    }

    @Test
    void searchQueryIgnoresPrefixesAndApostropheKinds() {
        assertThat(AddressDirectoryService.normalizeQuery("  с. Круп ")).isEqualTo("круп");
        assertThat(AddressDirectoryService.normalizeQuery("Кам'янець")).isEqualTo("кам’янець");
        assertThat(AddressDirectoryService.normalizeQuery("м.Луцьк")).isEqualTo("луцьк");
        assertThat(AddressDirectoryService.regionLabel("Автономна Республіка Крим", "O")).isEqualTo("Автономна Республіка Крим");
    }
}
