# CocoaPods 1.17 returns subprocess output as ASCII-8BIT. React Native's Hermes
# podspec interpolates that output into UTF-8 strings, which fails when the
# checkout path contains non-ASCII characters. Normalize command output at the
# CocoaPods boundary instead of patching generated Podfiles or node_modules.
require 'cocoapods'

module MatrixCocoaPodsUtf8
  def execute_command(executable, command, raise_on_failure = true)
    super.force_encoding(Encoding::UTF_8)
  end
end

Pod::Executable.singleton_class.prepend(MatrixCocoaPodsUtf8)
