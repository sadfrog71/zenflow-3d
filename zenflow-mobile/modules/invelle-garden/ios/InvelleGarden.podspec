Pod::Spec.new do |s|
  s.name = 'InvelleGarden'
  s.version = '1.0.0'
  s.summary = 'Restricted offline binary garden scene host'
  s.description = s.summary
  s.license = { :type => 'Private' }
  s.author = 'Invelle'
  s.homepage = 'https://invelleai.com'
  s.platforms = { :ios => '16.4' }
  s.source = { :git => '' }
  s.static_framework = true
  s.dependency 'ExpoModulesCore'
  s.frameworks = 'WebKit'
  s.source_files = '**/*.swift'
  s.swift_version = '5.9'
end
