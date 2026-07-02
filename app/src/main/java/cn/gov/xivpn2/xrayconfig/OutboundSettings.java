package cn.gov.xivpn2.xrayconfig;

import java.util.ArrayList;
import java.util.List;

public class OutboundSettings {
    public String domainStrategy;      // "AsIs"
    public List<FinalRule> finalRules; // Правила фильтрации

    public OutboundSettings() {
        this.finalRules = new ArrayList<>();
    }
}