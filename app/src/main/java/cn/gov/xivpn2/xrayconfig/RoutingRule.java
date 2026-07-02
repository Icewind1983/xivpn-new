package cn.gov.xivpn2.xrayconfig;

import java.util.List;
import java.util.Map;

public class RoutingRule {
    public String type = "field";
    public List<String> domain;
    public List<String> ip;
    public String port;
    public String network;
    public List<String> protocol;
    public String outboundTag;
    public List<String> inboundTag;
    public List<String> process;

    public String outboundSubscription;
    public String outboundLabel;
    public String label;
    public String sniffedTag;
    //public Map<String, List<String>> dns;
    //public String action;
    
    //public Boolean blockAds = false;
}
