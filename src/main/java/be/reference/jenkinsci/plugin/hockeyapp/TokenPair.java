package be.reference.jenkinsci.plugin.hockeyapp;

import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

public class TokenPair {
    private String tokenPairName;
    private Secret apiToken;

    public TokenPair() {
    }

    @DataBoundConstructor
    public TokenPair(String tokenPairName, Secret apiToken) {
        this.tokenPairName = tokenPairName;
        this.apiToken = apiToken;
    }

    public String getTokenPairName() {
        return tokenPairName;
    }

    public void setTokenPairName(String tokenPairName) {
        this.tokenPairName = tokenPairName;
    }

    public Secret getApiToken() {
        return apiToken;
    }

    public void setApiToken(Secret apiToken) {
        this.apiToken = apiToken;
    }

}
