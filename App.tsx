import React, { useEffect, useState } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  FlatList,
  Switch,
  StatusBar,
  Alert,
  SafeAreaView,
  TextInput,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

interface AppInfo {
  packageName: string;
  appName: string;
  uid: number;
  isSystemApp: boolean;
}

type TabType = 'firewall' | 'dns' | 'logs' | 'settings';

// Mock data for demo (real implementation needs native module)
const MOCK_APPS: AppInfo[] = [
  { packageName: 'com.facebook.katana', appName: 'Facebook', uid: 10001, isSystemApp: false },
  { packageName: 'com.instagram.android', appName: 'Instagram', uid: 10002, isSystemApp: false },
  { packageName: 'com.whatsapp', appName: 'WhatsApp', uid: 10003, isSystemApp: false },
  { packageName: 'com.twitter.android', appName: 'Twitter/X', uid: 10004, isSystemApp: false },
  { packageName: 'com.google.android.youtube', appName: 'YouTube', uid: 10005, isSystemApp: false },
  { packageName: 'com.spotify.music', appName: 'Spotify', uid: 10006, isSystemApp: false },
  { packageName: 'com.netflix.mediaclient', appName: 'Netflix', uid: 10007, isSystemApp: false },
  { packageName: 'com.tiktok.android', appName: 'TikTok', uid: 10008, isSystemApp: false },
  { packageName: 'com.snapchat.android', appName: 'Snapchat', uid: 10009, isSystemApp: false },
  { packageName: 'com.amazon.mShop.android', appName: 'Amazon', uid: 10010, isSystemApp: false },
];

interface LogEntry {
  action: string;
  destIp: string;
  destPort: number;
  timestamp: number;
  appName: string;
}

export default function App() {
  const [vpnEnabled, setVpnEnabled] = useState(false);
  const [apps, setApps] = useState<AppInfo[]>([]);
  const [blockedApps, setBlockedApps] = useState<Record<string, boolean>>({});
  const [currentTab, setCurrentTab] = useState<TabType>('firewall');
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [blockedDomains, setBlockedDomains] = useState<string[]>([]);
  const [newDomain, setNewDomain] = useState('');
  const [startOnBoot, setStartOnBoot] = useState(false);
  const [blockAds, setBlockAds] = useState(true);

  useEffect(() => {
    loadApps();
    loadBlockedApps();
    loadSettings();
  }, []);

  const loadApps = async () => {
    // In real app, this would call native module
    // For demo, use mock data
    setApps(MOCK_APPS);
  };

  const loadBlockedApps = async () => {
    try {
      const stored = await AsyncStorage.getItem('blockedApps');
      if (stored) {
        setBlockedApps(JSON.parse(stored));
      }
    } catch (error) {
      console.error('Error loading blocked apps:', error);
    }
  };

  const loadSettings = async () => {
    try {
      const bootSetting = await AsyncStorage.getItem('startOnBoot');
      const adsSetting = await AsyncStorage.getItem('blockAds');
      if (bootSetting) setStartOnBoot(JSON.parse(bootSetting));
      if (adsSetting) setBlockAds(JSON.parse(adsSetting));
    } catch (error) {
      console.error('Error loading settings:', error);
    }
  };

  const saveBlockedApps = async (newBlocked: Record<string, boolean>) => {
    try {
      await AsyncStorage.setItem('blockedApps', JSON.stringify(newBlocked));
    } catch (error) {
      console.error('Error saving blocked apps:', error);
    }
  };

  const toggleAppBlock = (packageName: string) => {
    const newBlocked = { ...blockedApps, [packageName]: !blockedApps[packageName] };
    setBlockedApps(newBlocked);
    saveBlockedApps(newBlocked);
  };

  const toggleVpn = async () => {
    if (!vpnEnabled) {
      Alert.alert(
        'VPN Permission Required',
        'This app needs VPN permission to filter network traffic. In a full implementation, this would request Android VPN permission.',
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Enable',
            onPress: () => {
              setVpnEnabled(true);
              // Generate some mock logs
              generateMockLogs();
            }
          }
        ]
      );
    } else {
      setVpnEnabled(false);
    }
  };

  const generateMockLogs = () => {
    const blockedList = Object.keys(blockedApps).filter(k => blockedApps[k]);
    const mockLogs: LogEntry[] = blockedList.slice(0, 5).map((pkg, i) => ({
      action: 'BLOCKED',
      destIp: `${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`,
      destPort: [80, 443, 8080][Math.floor(Math.random() * 3)],
      timestamp: Date.now() - i * 60000,
      appName: apps.find(a => a.packageName === pkg)?.appName || pkg,
    }));
    setLogs(mockLogs);
  };

  const loadBlockList = () => {
    Alert.alert(
      'Load Block List',
      'Loading StevenBlack hosts list would block approximately 130,000 ad and tracker domains.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Load',
          onPress: () => {
            setBlockedDomains(['ads.google.com', 'tracker.facebook.com', 'analytics.tiktok.com']);
            Alert.alert('Success', 'Loaded 130,000+ blocked domains (demo)');
          }
        }
      ]
    );
  };

  const addCustomDomain = () => {
    if (newDomain.trim()) {
      setBlockedDomains([...blockedDomains, newDomain.trim()]);
      setNewDomain('');
    }
  };

  const renderAppItem = ({ item }: { item: AppInfo }) => (
    <View style={styles.appItem}>
      <View style={styles.appInfo}>
        <Text style={styles.appName}>{item.appName}</Text>
        <Text style={styles.packageName}>{item.packageName}</Text>
      </View>
      <Switch
        value={blockedApps[item.packageName] || false}
        onValueChange={() => toggleAppBlock(item.packageName)}
        trackColor={{ false: '#3a3a5a', true: '#FF6B35' }}
        thumbColor={blockedApps[item.packageName] ? '#fff' : '#888'}
      />
    </View>
  );

  const renderLogItem = ({ item }: { item: LogEntry }) => (
    <View style={styles.logItem}>
      <View style={styles.logHeader}>
        <Text style={styles.logAction}>{item.action}</Text>
        <Text style={styles.logApp}>{item.appName}</Text>
      </View>
      <Text style={styles.logDetails}>
        {item.destIp}:{item.destPort}
      </Text>
      <Text style={styles.logTime}>
        {new Date(item.timestamp).toLocaleTimeString()}
      </Text>
    </View>
  );

  const blockedCount = Object.values(blockedApps).filter(Boolean).length;

  const renderFirewallTab = () => (
    <View style={styles.flex1}>
      <View style={styles.statsBar}>
        <View style={styles.statItem}>
          <Text style={styles.statNumber}>{apps.length}</Text>
          <Text style={styles.statLabel}>Apps</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={[styles.statNumber, { color: '#FF6B35' }]}>{blockedCount}</Text>
          <Text style={styles.statLabel}>Blocked</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={[styles.statNumber, { color: '#4CAF50' }]}>{apps.length - blockedCount}</Text>
          <Text style={styles.statLabel}>Allowed</Text>
        </View>
      </View>
      <FlatList
        data={apps}
        renderItem={renderAppItem}
        keyExtractor={(item) => item.packageName}
        contentContainerStyle={styles.listContent}
      />
    </View>
  );

  const renderDnsTab = () => (
    <View style={styles.tabContent}>
      <Text style={styles.sectionTitle}>DNS Filtering</Text>
      <Text style={styles.helpText}>
        Block ads and trackers by filtering DNS requests. This works at the network level.
      </Text>

      <TouchableOpacity style={styles.loadButton} onPress={loadBlockList}>
        <Text style={styles.loadButtonText}>Load StevenBlack Hosts</Text>
      </TouchableOpacity>

      <View style={styles.domainInputRow}>
        <TextInput
          style={styles.domainInput}
          placeholder="Add custom domain..."
          placeholderTextColor="#666"
          value={newDomain}
          onChangeText={setNewDomain}
          onSubmitEditing={addCustomDomain}
        />
        <TouchableOpacity style={styles.addButton} onPress={addCustomDomain}>
          <Text style={styles.addButtonText}>+</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.subTitle}>Blocked Domains ({blockedDomains.length})</Text>
      {blockedDomains.map((domain, i) => (
        <View key={i} style={styles.domainItem}>
          <Text style={styles.domainText}>{domain}</Text>
          <TouchableOpacity onPress={() => setBlockedDomains(blockedDomains.filter((_, idx) => idx !== i))}>
            <Text style={styles.removeText}>X</Text>
          </TouchableOpacity>
        </View>
      ))}
    </View>
  );

  const renderLogsTab = () => (
    <View style={styles.flex1}>
      <View style={styles.logsHeader}>
        <Text style={styles.sectionTitle}>Connection Logs</Text>
        <TouchableOpacity onPress={() => setLogs([])}>
          <Text style={styles.clearText}>Clear</Text>
        </TouchableOpacity>
      </View>
      <FlatList
        data={logs}
        renderItem={renderLogItem}
        keyExtractor={(item, index) => `${item.timestamp}-${index}`}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>
              {vpnEnabled ? 'No blocked connections yet' : 'Enable VPN to see logs'}
            </Text>
          </View>
        }
      />
    </View>
  );

  const renderSettingsTab = () => (
    <View style={styles.tabContent}>
      <Text style={styles.sectionTitle}>Settings</Text>

      <View style={styles.settingItem}>
        <View>
          <Text style={styles.settingLabel}>Start on boot</Text>
          <Text style={styles.settingDesc}>Auto-start firewall when device boots</Text>
        </View>
        <Switch
          value={startOnBoot}
          onValueChange={(v) => {
            setStartOnBoot(v);
            AsyncStorage.setItem('startOnBoot', JSON.stringify(v));
          }}
          trackColor={{ false: '#3a3a5a', true: '#FF6B35' }}
          thumbColor={startOnBoot ? '#fff' : '#888'}
        />
      </View>

      <View style={styles.settingItem}>
        <View>
          <Text style={styles.settingLabel}>Block ads by default</Text>
          <Text style={styles.settingDesc}>Use DNS filtering for ad blocking</Text>
        </View>
        <Switch
          value={blockAds}
          onValueChange={(v) => {
            setBlockAds(v);
            AsyncStorage.setItem('blockAds', JSON.stringify(v));
          }}
          trackColor={{ false: '#3a3a5a', true: '#FF6B35' }}
          thumbColor={blockAds ? '#fff' : '#888'}
        />
      </View>

      <View style={styles.aboutSection}>
        <Text style={styles.aboutTitle}>About Firewall</Text>
        <Text style={styles.aboutText}>
          Firewall is an Android app that lets you control which apps can access the internet.
          It uses Android's VpnService API to filter traffic without requiring root access.
        </Text>
        <Text style={styles.versionText}>Version 1.0.0</Text>
      </View>
    </View>
  );

  const renderContent = () => {
    switch (currentTab) {
      case 'firewall':
        return renderFirewallTab();
      case 'dns':
        return renderDnsTab();
      case 'logs':
        return renderLogsTab();
      case 'settings':
        return renderSettingsTab();
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#1a1a2e" />

      <View style={styles.header}>
        <View>
          <Text style={styles.title}>Firewall</Text>
          <Text style={styles.subtitle}>Network Control</Text>
        </View>
        <TouchableOpacity
          style={[styles.vpnButton, vpnEnabled && styles.vpnButtonActive]}
          onPress={toggleVpn}
        >
          <View style={[styles.vpnIndicator, vpnEnabled && styles.vpnIndicatorActive]} />
          <Text style={styles.vpnButtonText}>
            {vpnEnabled ? 'Active' : 'Off'}
          </Text>
        </TouchableOpacity>
      </View>

      <View style={styles.content}>{renderContent()}</View>

      <View style={styles.tabBar}>
        {(['firewall', 'dns', 'logs', 'settings'] as TabType[]).map((tab) => (
          <TouchableOpacity
            key={tab}
            style={[styles.tab, currentTab === tab && styles.tabActive]}
            onPress={() => setCurrentTab(tab)}
          >
            <Text style={[styles.tabText, currentTab === tab && styles.tabTextActive]}>
              {tab.charAt(0).toUpperCase() + tab.slice(1)}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1a1a2e',
  },
  flex1: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#2a2a4e',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#FF6B35',
  },
  subtitle: {
    fontSize: 12,
    color: '#888',
    marginTop: 2,
  },
  vpnButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2a2a4e',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    gap: 8,
  },
  vpnButtonActive: {
    backgroundColor: '#FF6B35',
  },
  vpnIndicator: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#666',
  },
  vpnIndicatorActive: {
    backgroundColor: '#4CAF50',
  },
  vpnButtonText: {
    color: '#fff',
    fontWeight: '600',
  },
  content: {
    flex: 1,
  },
  statsBar: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#2a2a4e',
    backgroundColor: '#16213e',
  },
  statItem: {
    alignItems: 'center',
  },
  statNumber: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#fff',
  },
  statLabel: {
    fontSize: 12,
    color: '#888',
    marginTop: 4,
  },
  tabContent: {
    flex: 1,
    padding: 20,
  },
  listContent: {
    paddingVertical: 8,
  },
  appItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#2a2a4e',
  },
  appInfo: {
    flex: 1,
    marginRight: 16,
  },
  appName: {
    fontSize: 16,
    fontWeight: '500',
    color: '#fff',
  },
  packageName: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 8,
  },
  subTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
    marginTop: 24,
    marginBottom: 12,
  },
  helpText: {
    fontSize: 14,
    color: '#888',
    marginBottom: 20,
    lineHeight: 20,
  },
  loadButton: {
    backgroundColor: '#FF6B35',
    paddingVertical: 14,
    borderRadius: 8,
    alignItems: 'center',
  },
  loadButtonText: {
    color: '#fff',
    fontWeight: '600',
    fontSize: 16,
  },
  domainInputRow: {
    flexDirection: 'row',
    marginTop: 16,
    gap: 8,
  },
  domainInput: {
    flex: 1,
    backgroundColor: '#2a2a4e',
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: '#fff',
    fontSize: 14,
  },
  addButton: {
    backgroundColor: '#FF6B35',
    width: 48,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  addButtonText: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
  },
  domainItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#2a2a4e',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 8,
    marginBottom: 8,
  },
  domainText: {
    color: '#fff',
    fontSize: 14,
  },
  removeText: {
    color: '#FF6B35',
    fontSize: 16,
    fontWeight: 'bold',
  },
  logsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#2a2a4e',
  },
  clearText: {
    color: '#FF6B35',
    fontSize: 14,
  },
  logItem: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#2a2a4e',
  },
  logHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  logAction: {
    fontSize: 14,
    fontWeight: '600',
    color: '#FF6B35',
  },
  logApp: {
    fontSize: 12,
    color: '#888',
  },
  logDetails: {
    fontSize: 14,
    color: '#fff',
    marginTop: 4,
    fontFamily: 'monospace',
  },
  logTime: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 60,
  },
  emptyText: {
    textAlign: 'center',
    color: '#666',
    fontSize: 16,
  },
  settingItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#2a2a4e',
  },
  settingLabel: {
    fontSize: 16,
    color: '#fff',
  },
  settingDesc: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  aboutSection: {
    marginTop: 32,
    padding: 16,
    backgroundColor: '#16213e',
    borderRadius: 12,
  },
  aboutTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 8,
  },
  aboutText: {
    fontSize: 14,
    color: '#888',
    lineHeight: 20,
  },
  versionText: {
    fontSize: 12,
    color: '#666',
    marginTop: 12,
  },
  tabBar: {
    flexDirection: 'row',
    backgroundColor: '#16213e',
    borderTopWidth: 1,
    borderTopColor: '#2a2a4e',
  },
  tab: {
    flex: 1,
    paddingVertical: 16,
    alignItems: 'center',
  },
  tabActive: {
    borderTopWidth: 2,
    borderTopColor: '#FF6B35',
  },
  tabText: {
    fontSize: 14,
    color: '#666',
  },
  tabTextActive: {
    color: '#FF6B35',
    fontWeight: '600',
  },
});
