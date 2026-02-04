const { withGradleProperties } = require('@expo/config-plugins');

module.exports = function withKotlinVersion(config) {
  return withGradleProperties(config, (config) => {
    // Force Kotlin version 1.9.25 for Compose compatibility
    const existingKotlin = config.modResults.find(
      item => item.type === 'property' && item.key === 'kotlin.version'
    );

    if (existingKotlin) {
      existingKotlin.value = '1.9.25';
    } else {
      config.modResults.push({
        type: 'property',
        key: 'kotlin.version',
        value: '1.9.25'
      });
    }

    // Also add android.kotlinVersion for expo-build-properties
    const existingAndroidKotlin = config.modResults.find(
      item => item.type === 'property' && item.key === 'android.kotlinVersion'
    );

    if (existingAndroidKotlin) {
      existingAndroidKotlin.value = '1.9.25';
    } else {
      config.modResults.push({
        type: 'property',
        key: 'android.kotlinVersion',
        value: '1.9.25'
      });
    }

    // Suppress Kotlin version check as backup
    const existingSuppressCheck = config.modResults.find(
      item => item.type === 'property' && item.key === 'android.suppressKotlinVersionCompatibilityCheck'
    );

    if (!existingSuppressCheck) {
      config.modResults.push({
        type: 'property',
        key: 'android.suppressKotlinVersionCompatibilityCheck',
        value: 'true'
      });
    }

    return config;
  });
};
