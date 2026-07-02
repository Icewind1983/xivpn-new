package cn.gov.xivpn2.xrayconfig;

import java.util.ArrayList;
import java.util.List;

public class FinalRule {
    public String action;           // "block" или "direct"
    public List<String> domain;     // ["geosite:category-ads"]

    public FinalRule() {
        this.domain = new ArrayList<>();
    }

    public FinalRule(String action, List<String> domains) {
        this.action = action;
        this.domain = new ArrayList<>(domains);
    }
}