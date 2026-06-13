class Recall < Formula
  desc "Personal engineering memory manager — CLI tool that stores learnings as markdown"
  homepage "https://github.com/twincie/recall"
  url "https://github.com/twincie/recall/releases/download/v1.0.0/recall-1.0.0.jar"
  sha256 "0000000000000000000000000000000000000000000000000000000000000000"
  license "MIT"

  depends_on "openjdk@17"

  def install
    libexec.install "recall-1.0.0.jar"
    bin.write_jar_script libexec/"recall-1.0.0.jar", "recall"
  end

  test do
    assert_match "1.0.0", shell_output("#{bin}/recall --version")
  end
end
