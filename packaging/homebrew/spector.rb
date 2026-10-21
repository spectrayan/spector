# typed: false
# frozen_string_literal: true

# ═══════════════════════════════════════════════════════════════════
# Spector Homebrew Formula (spectrayan/homebrew-spector)
# ═══════════════════════════════════════════════════════════════════
class Spector < Formula
  desc "Cognitive memory engine for AI agents"
  homepage "https://github.com/spectrayan/spector"
  url "https://github.com/spectrayan/spector/releases/download/v0.1.0-beta.1/spector.jar"
  sha256 "5ac036fee563480deb6efe195b72a6c4ec540f769f1984b6d880aac835d584c2"
  license "Apache-2.0"
  version "0.1.0-beta.1"

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
