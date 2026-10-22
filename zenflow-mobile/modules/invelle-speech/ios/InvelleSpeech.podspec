Pod::Spec.new do |s|
  s.name = 'InvelleSpeech'
  s.version = '1.0.0'
  s.summary = 'Invelle on-device voice draft input'
  s.description = s.summary
  s.license = { :type => 'Private' }
  s.author = 'Invelle'
  s.homepage = 'https://invelleai.com'
  s.platforms = { :ios => '16.4' }
  s.source = { :git => '' }
  s.static_framework = true
  s.dependency 'ExpoModulesCore'
  s.frameworks = 'Speech', 'AVFoundation'
  s.source_files = '**/*.swift'
  s.swift_version = '5.9'
end
