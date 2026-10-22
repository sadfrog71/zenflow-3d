# Adds a local-only UI test target to the generated, ignored iOS project.
# No application source, dependency, signing or production configuration is changed.
require 'xcodeproj'
root = File.expand_path('../../..', __dir__)
project = Xcodeproj::Project.open(File.join(root, 'ios/Invelle.xcodeproj'))
name = 'InvelleGardenUITests'
target = project.targets.find { |t| t.name == name } || project.new_target(:ui_test_bundle, name, :ios, '16.4')
app = project.targets.find { |t| t.name == 'Invelle' }
target.add_dependency(app) unless target.dependencies.any? { |d| d.target == app }
source = File.join(__dir__, 'GardenUITests.swift')
ref = project.files.find { |f| f.real_path.to_s == source } || project.main_group.new_file(source)
target.source_build_phase.add_file_reference(ref) unless target.source_build_phase.files_references.include?(ref)
target.build_configurations.each do |config|
  config.build_settings.merge!('PRODUCT_NAME'=>name, 'PRODUCT_MODULE_NAME'=>name, 'PRODUCT_BUNDLE_IDENTIFIER'=>'com.zenflow.local.gardenuitests', 'SWIFT_VERSION'=>'5.0', 'GENERATE_INFOPLIST_FILE'=>'YES', 'TEST_TARGET_NAME'=>'Invelle', 'TARGETED_DEVICE_FAMILY'=>'1,2', 'CODE_SIGNING_ALLOWED'=>'NO')
end
project.save
scheme=Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.add_test_target(target)
scheme.save_as(File.join(root,'ios/Invelle.xcodeproj'), name, true)
puts "Prepared local UI test target #{name}"
