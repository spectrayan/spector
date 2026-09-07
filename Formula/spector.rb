# typed: false
# frozen_string_literal: true

# ═══════════════════════════════════════════════════════════════════
# Spector Homebrew Formula (spectrayan/homebrew-spector or spector tap)
# ═══════════════════════════════════════════════════════════════════
class Spector < Formula
  desc "Zero-overhead, agent-ready AI memory backbone and search engine"
  homepage "https://github.com/spectrayan/spector"
  url "https://github.com/spectrayan/spector/releases/download/v0.1.0-alpha/spector.jar"
  sha256 "0000000000000000000000000000000000000000000000000000000000000000" # Updated on release
  license "Apache-2.0"
  version "0.1.0-alpha"

  depends_on "openjdk@25"

  def install
    libexec.install "spector.jar"
    (bin/"spector").write <<~EOS
      #!/usr/bin/env bash
      exec "#{Formula["openjdk@25"].opt_bin}/java" \\
        --enable-preview \\
        --add-modules=jdk.incubator.vector \\
        --enable-native-access=ALL-UNNAMED \\
        -jar "#{libexec}/spector.jar" "$@"
    EOS
  end

  def caveats
    <<~EOS
      Spector requires Java 25 with the Vector API incubator module.
      
      To initialize and verify your installation:
        spector init
        spector doctor
    EOS
  end

  test do
    assert_match "spector", shell_output("#{bin}/spector --help")
  end
end

