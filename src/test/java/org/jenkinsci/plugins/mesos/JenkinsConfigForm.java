package org.jenkinsci.plugins.mesos;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class JenkinsConfigForm {

  public static class Builder {
    final List<String> values = new ArrayList<>();

    public Builder add(String key, String value) throws UnsupportedEncodingException {
      final String pair = URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8");
      this.values.add(pair);
      return this;
    }

    public Builder addRaw(String key, String value) {
      final String pair = key + "=" + value;
      this.values.add(pair);
      return this;
    }

    public String build() {
      return String.join("&", this.values);
    }
  }
}
