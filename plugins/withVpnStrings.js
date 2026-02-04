const { withStringsXml } = require('@expo/config-plugins');

module.exports = function withVpnStrings(config) {
  return withStringsXml(config, (config) => {
    const strings = config.modResults.resources.string || [];

    // Add VPN notification strings if not present
    const vpnStrings = [
      { $: { name: 'vpn_notification_title' }, _: 'Firewall Active' },
      { $: { name: 'vpn_notification_content' }, _: 'Monitoring network traffic' },
      { $: { name: 'vpn_channel_name' }, _: 'VPN Service' },
      { $: { name: 'vpn_channel_description' }, _: 'Notifications for VPN service status' }
    ];

    vpnStrings.forEach(newString => {
      const exists = strings.some(s => s.$ && s.$.name === newString.$.name);
      if (!exists) {
        strings.push(newString);
      }
    });

    config.modResults.resources.string = strings;
    return config;
  });
};
