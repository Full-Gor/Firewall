const { withStringsXml } = require('@expo/config-plugins');

module.exports = function withVpnStrings(config) {
  return withStringsXml(config, (config) => {
    // Add VPN notification strings
    const strings = config.modResults.resources.string || [];

    const vpnStrings = [
      { $: { name: 'vpn_notification_channel' }, _: 'Firewall VPN' },
      { $: { name: 'vpn_notification_title' }, _: 'Firewall Active' },
      { $: { name: 'vpn_notification_text' }, _: 'Filtering network traffic' },
    ];

    for (const vpnString of vpnStrings) {
      const exists = strings.find(s => s.$.name === vpnString.$.name);
      if (!exists) {
        strings.push(vpnString);
      }
    }

    config.modResults.resources.string = strings;
    return config;
  });
};
