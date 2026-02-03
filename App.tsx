import React, { useEffect, useState } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  FlatList,
  Switch,
  StatusBar,
  NativeModules,
  Alert,
  SafeAreaView,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

const { FirewallModule } = NativeModules;

interface AppInfo {
  packageName: string;
  appName: string;
  uid: number;
  isSystemApp: boolean;
}

type TabType = 'firewall' | 'dns' | 'logs' | 'settings';

export default function App() {
  const [vpnEnabled, setVpnEnabled] = useState(false);
  const [apps, setApps] = useState<AppInfo[]>([]);
  const [blockedApps, setBlockedApps] = useState<Record<string, boolean>>({});
  const [currentTab, setCurrentTab] = useState<TabType>('firewall');
  const [logs, setLogs] = useState<any[]>([]);

  useEffect(() => {
    loadApps();
    loadBlockedApps();
    checkVpnStatus();
  }, []);

  const checkVpnStatus = async () => {
    try {
      if (FirewallModule) {
        const running = await FirewallModule.isVpnRunning();
        setVpnEnabled(running);
      }
    } catch (error) {
      console.error('Error checking VPN status:', error);
    }
  };

  const loadApps = async () => {
    try {
      if (FirewallModule) {
        const installedApps = await FirewallModule.getInstalledApps();
        const sortedApps = installedApps.sort((a: AppInfo, b: AppInfo) =>
          a.appName.localeCompare(b.appName)
        );
        setApps(sortedApps);
      }
    } catch (error) {
      console.error('Error loading apps:', error);
    }
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

  const saveBlockedApps = async (newBlocked: Record<string, boolean>) => {
    try {
      await AsyncStorage.setItem('blockedApps', JSON.stringify(newBlocked));
      if (FirewallModule) {
        const blockedList = Object.keys(newBlocked).filter((k) => newBlocked[k]);
        await FirewallModule.setBlockedApps(blockedList);
      }
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
    try {
      if (!FirewallModule) {
        Alert.alert('Error', 'Firewall module not available');
        return;
      }
      if (!vpnEnabled) {
        const hasPermission = await FirewallModule.requestVpnPermission();
        if (hasPermission) {
          await FirewallModule.startVpn();
          setVpnEnabled(true);
        }
      } else {
        await FirewallModule.stopVpn();
        setVpnEnabled(false);
      }
    } catch (error: any) {
      Alert.alert('Error', error.message || 'Failed to toggle VPN');
    }
  };

  const loadLogs = async () => {
    try {
      if (FirewallModule) {
        const connectionLogs = await FirewallModule.getConnectionLogs(100);
        setLogs(connectionLogs);
      }
    } catch (error) {
      console.error('Error loading logs:', error);
    }
  };

  useEffect(() => {
    if (currentTab === 'logs') {
      loadLogs();
    }
  }, [currentTab]);

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

  const renderLogItem = ({ item }: { item: any }) => (
    <View style={styles.logItem}>
      <Text style={styles.logAction}>{item.action}</Text>
      <Text style={styles.logDetails}>
        {item.destIp}:{item.destPort}
      </Text>
      <Text style={styles.logTime}>
        {new Date(item.timestamp).toLocaleTimeString()}
      </Text>
    </View>
  );

  const renderFirewallTab = () => (
    <FlatList
      data={apps}
      renderItem={renderAppItem}
      keyExtractor={(item) => item.packageName}
      contentContainerStyle={styles.listContent}
      ListEmptyComponent={
        <Text style={styles.emptyText}>Loading apps...</Text>
      }
    />
  );

  const renderDnsTab = () => (
    <View style={styles.tabContent}>
      <Text style={styles.sectionTitle}>Blocked Domains</Text>
      <Text style={styles.helpText}>
        Block ads and trackers by adding domains to the blocklist.
      </Text>
      <TouchableOpacity
        style={styles.loadButton}
        onPress={async () => {
          try {
            if (FirewallModule) {
              const count = await FirewallModule.loadBlockList(
                'https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts'
              );
              Alert.alert('Success', `Loaded ${count} domains`);
            }
          } catch (error: any) {
            Alert.alert('Error', error.message);
          }
        }}
      >
        <Text style={styles.loadButtonText}>Load StevenBlack Hosts</Text>
      </TouchableOpacity>
    </View>
  );

  const renderLogsTab = () => (
    <FlatList
      data={logs}
      renderItem={renderLogItem}
      keyExtractor={(item, index) => `${item.timestamp}-${index}`}
      contentContainerStyle={styles.listContent}
      ListEmptyComponent={
        <Text style={styles.emptyText}>No blocked connections yet</Text>
      }
    />
  );

  const renderSettingsTab = () => (
    <View style={styles.tabContent}>
      <Text style={styles.sectionTitle}>Settings</Text>
      <View style={styles.settingItem}>
        <Text style={styles.settingLabel}>Start on boot</Text>
        <Switch
          value={false}
          trackColor={{ false: '#3a3a5a', true: '#FF6B35' }}
        />
      </View>
      <View style={styles.settingItem}>
        <Text style={styles.settingLabel}>Block system apps</Text>
        <Switch
          value={false}
          trackColor={{ false: '#3a3a5a', true: '#FF6B35' }}
        />
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
        <Text style={styles.title}>Firewall</Text>
        <TouchableOpacity
          style={[styles.vpnButton, vpnEnabled && styles.vpnButtonActive]}
          onPress={toggleVpn}
        >
          <Text style={styles.vpnButtonText}>
            {vpnEnabled ? 'VPN Active' : 'Start VPN'}
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
            <Text
              style={[styles.tabText, currentTab === tab && styles.tabTextActive]}
            >
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
  vpnButton: {
    backgroundColor: '#2a2a4e',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 20,
  },
  vpnButtonActive: {
    backgroundColor: '#FF6B35',
  },
  vpnButtonText: {
    color: '#fff',
    fontWeight: '600',
  },
  content: {
    flex: 1,
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
    paddingVertical: 12,
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
    color: '#888',
    marginTop: 2,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 12,
  },
  helpText: {
    fontSize: 14,
    color: '#888',
    marginBottom: 20,
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
  logItem: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#2a2a4e',
  },
  logAction: {
    fontSize: 14,
    fontWeight: '600',
    color: '#FF6B35',
  },
  logDetails: {
    fontSize: 14,
    color: '#fff',
    marginTop: 4,
  },
  logTime: {
    fontSize: 12,
    color: '#888',
    marginTop: 2,
  },
  emptyText: {
    textAlign: 'center',
    color: '#888',
    marginTop: 40,
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
    color: '#888',
  },
  tabTextActive: {
    color: '#FF6B35',
    fontWeight: '600',
  },
});
