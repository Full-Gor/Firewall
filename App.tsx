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
  TextInput,
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
  const [blockedDomains, setBlockedDomains] = useState<string[]>([]);
  const [newDomain, setNewDomain] = useState('');
  const [domainCount, setDomainCount] = useState(0);
  const [logsCount, setLogsCount] = useState(0);
  const [startOnBoot, setStartOnBoot] = useState(false);
  const [blockSystemApps, setBlockSystemApps] = useState(false);

  useEffect(() => {
    loadApps();
    loadBlockedApps();
    checkVpnStatus();
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      if (FirewallModule) {
        const bootEnabled = await FirewallModule.getStartOnBoot();
        setStartOnBoot(bootEnabled);
        const systemAppsEnabled = await FirewallModule.getBlockSystemApps();
        setBlockSystemApps(systemAppsEnabled);
      }
    } catch (error) {
      console.error('Error loading settings:', error);
    }
  };

  const toggleStartOnBoot = async (value: boolean) => {
    try {
      if (FirewallModule) {
        await FirewallModule.setStartOnBoot(value);
        setStartOnBoot(value);
      }
    } catch (error: any) {
      Alert.alert('Erreur', error.message);
    }
  };

  const toggleBlockSystemApps = async (value: boolean) => {
    try {
      if (FirewallModule) {
        await FirewallModule.setBlockSystemApps(value);
        setBlockSystemApps(value);
        // Reload apps list to include/exclude system apps
        loadApps();
      }
    } catch (error: any) {
      Alert.alert('Erreur', error.message);
    }
  };

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
        setLogs(connectionLogs || []);
        setLogsCount(connectionLogs?.length || 0);
      }
    } catch (error) {
      console.error('Error loading logs:', error);
    }
  };

  const clearLogs = async () => {
    try {
      if (FirewallModule) {
        await FirewallModule.clearConnectionLogs();
        setLogs([]);
        setLogsCount(0);
      }
    } catch (error) {
      console.error('Error clearing logs:', error);
    }
  };

  // Auto-refresh logs every 5 seconds when on logs tab
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (currentTab === 'logs' && vpnEnabled) {
      interval = setInterval(loadLogs, 5000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [currentTab, vpnEnabled]);

  useEffect(() => {
    if (currentTab === 'logs') {
      loadLogs();
    }
    if (currentTab === 'dns') {
      loadBlockedDomains();
    }
  }, [currentTab]);

  const loadBlockedDomains = async () => {
    try {
      const stored = await AsyncStorage.getItem('customBlockedDomains');
      if (stored) {
        setBlockedDomains(JSON.parse(stored));
      }
      // Get total count from native module
      if (FirewallModule && FirewallModule.getBlockedDomainsCount) {
        const count = await FirewallModule.getBlockedDomainsCount();
        setDomainCount(count);
      }
    } catch (error) {
      console.error('Error loading blocked domains:', error);
    }
  };

  const addBlockedDomain = async () => {
    if (!newDomain.trim()) return;

    const domain = newDomain.trim().toLowerCase();
    if (blockedDomains.includes(domain)) {
      Alert.alert('Erreur', 'Ce domaine est déjà bloqué');
      return;
    }

    try {
      if (FirewallModule) {
        await FirewallModule.addBlockedDomain(domain);
      }
      const updated = [...blockedDomains, domain];
      setBlockedDomains(updated);
      await AsyncStorage.setItem('customBlockedDomains', JSON.stringify(updated));
      setNewDomain('');
      setDomainCount(prev => prev + 1);
    } catch (error: any) {
      Alert.alert('Erreur', error.message);
    }
  };

  const removeBlockedDomain = async (domain: string) => {
    try {
      if (FirewallModule) {
        await FirewallModule.removeBlockedDomain(domain);
      }
      const updated = blockedDomains.filter(d => d !== domain);
      setBlockedDomains(updated);
      await AsyncStorage.setItem('customBlockedDomains', JSON.stringify(updated));
      setDomainCount(prev => prev - 1);
    } catch (error: any) {
      Alert.alert('Erreur', error.message);
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

  const renderLogItem = ({ item }: { item: any }) => {
    const isDns = item.action === 'DNS_BLOCKED';
    const actionIcon = isDns ? '🌐' : '🚫';
    const actionText = isDns ? 'Domaine bloqué' : 'Connexion bloquée';
    const appName = item.packageName?.split('.')?.pop() || '';

    return (
      <View style={styles.logItem}>
        <View style={styles.logHeader}>
          <Text style={styles.logAction}>{actionIcon} {actionText}</Text>
          <Text style={styles.logTime}>
            {new Date(item.timestamp).toLocaleTimeString('fr-FR')}
          </Text>
        </View>
        <Text style={styles.logDetails}>
          {isDns ? item.destIp : `${item.destIp}:${item.destPort}`}
        </Text>
        {appName ? (
          <Text style={styles.logApp}>App: {appName}</Text>
        ) : null}
      </View>
    );
  };

  const renderFirewallTab = () => (
    <View style={styles.tabContent}>
      <Text style={styles.sectionTitle}>Filtrage DNS</Text>
      <Text style={styles.helpText}>
        VPN actif = AdGuard DNS bloque pubs/trackers pour toutes les apps automatiquement.
      </Text>
      <View style={styles.infoBox}>
        <Text style={styles.infoBoxText}>
          Le VPN route tout le trafic DNS vers AdGuard (94.140.14.14) qui bloque automatiquement les pubs et trackers connus.
        </Text>
      </View>
    </View>
  );

  const renderDomainItem = ({ item }: { item: string }) => (
    <View style={styles.domainItem}>
      <Text style={styles.domainText}>{item}</Text>
      <TouchableOpacity
        style={styles.removeButton}
        onPress={() => removeBlockedDomain(item)}
      >
        <Text style={styles.removeButtonText}>✕</Text>
      </TouchableOpacity>
    </View>
  );

  const renderDnsTab = () => (
    <View style={styles.tabContent}>
      <Text style={styles.sectionTitle}>Domaines bloqués</Text>
      <Text style={styles.helpText}>
        Bloque les pubs et trackers. Total: {domainCount} domaines
      </Text>

      {/* Ajouter un domaine */}
      <View style={styles.addDomainContainer}>
        <TextInput
          style={styles.domainInput}
          placeholder="ex: ads.example.com"
          placeholderTextColor="#666"
          value={newDomain}
          onChangeText={setNewDomain}
          autoCapitalize="none"
          autoCorrect={false}
        />
        <TouchableOpacity style={styles.addButton} onPress={addBlockedDomain}>
          <Text style={styles.addButtonText}>+</Text>
        </TouchableOpacity>
      </View>

      {/* Charger liste pré-faite */}
      <TouchableOpacity
        style={styles.loadButton}
        onPress={async () => {
          try {
            if (FirewallModule) {
              Alert.alert('Chargement...', 'Téléchargement de la liste (~50,000 domaines)');
              const count = await FirewallModule.loadBlockList(
                'https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts'
              );
              setDomainCount(prev => prev + count);
              Alert.alert('Succès', `${count} domaines ajoutés`);
            }
          } catch (error: any) {
            Alert.alert('Erreur', error.message);
          }
        }}
      >
        <Text style={styles.loadButtonText}>📥 Charger liste anti-pubs (StevenBlack)</Text>
      </TouchableOpacity>

      {/* Liste des domaines custom */}
      <Text style={styles.subTitle}>Mes domaines ({blockedDomains.length})</Text>
      <FlatList
        data={blockedDomains}
        renderItem={renderDomainItem}
        keyExtractor={(item) => item}
        style={styles.domainList}
        ListEmptyComponent={
          <Text style={styles.emptyText}>Aucun domaine custom ajouté</Text>
        }
      />
    </View>
  );

  const renderLogsTab = () => (
    <View style={styles.tabContent}>
      <View style={styles.logsHeader}>
        <Text style={styles.sectionTitle}>Connexions bloquées</Text>
        <Text style={styles.logsCount}>{logsCount} logs</Text>
      </View>

      <View style={styles.logsButtons}>
        <TouchableOpacity style={styles.refreshButton} onPress={loadLogs}>
          <Text style={styles.refreshButtonText}>🔄 Rafraîchir</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.clearButton} onPress={clearLogs}>
          <Text style={styles.clearButtonText}>🗑️ Vider</Text>
        </TouchableOpacity>
      </View>

      {!vpnEnabled && (
        <View style={styles.warningBox}>
          <Text style={styles.warningText}>⚠️ Active le VPN pour voir les connexions bloquées</Text>
        </View>
      )}

      <FlatList
        data={logs}
        renderItem={renderLogItem}
        keyExtractor={(item, index) => `${item.timestamp}-${index}`}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <Text style={styles.emptyText}>
            {vpnEnabled ? 'Aucune connexion bloquée pour le moment' : 'Active le VPN pour commencer'}
          </Text>
        }
      />
    </View>
  );

  const renderSettingsTab = () => (
    <View style={styles.tabContent}>
      <Text style={styles.sectionTitle}>Paramètres</Text>

      <View style={styles.settingItem}>
        <View style={styles.settingInfo}>
          <Text style={styles.settingLabel}>Démarrage auto</Text>
          <Text style={styles.settingDesc}>Lancer le VPN au démarrage du téléphone</Text>
        </View>
        <Switch
          value={startOnBoot}
          onValueChange={toggleStartOnBoot}
          trackColor={{ false: '#3a3a5a', true: '#FF6B35' }}
          thumbColor={startOnBoot ? '#fff' : '#888'}
        />
      </View>

      <View style={styles.settingItem}>
        <View style={styles.settingInfo}>
          <Text style={styles.settingLabel}>Apps système</Text>
          <Text style={styles.settingDesc}>Afficher Chrome, Gmail, etc. dans la liste</Text>
        </View>
        <Switch
          value={blockSystemApps}
          onValueChange={toggleBlockSystemApps}
          trackColor={{ false: '#3a3a5a', true: '#FF6B35' }}
          thumbColor={blockSystemApps ? '#fff' : '#888'}
        />
      </View>

      <View style={styles.settingDivider} />

      <Text style={styles.settingSection}>Informations</Text>

      <View style={styles.infoItem}>
        <Text style={styles.infoLabel}>Apps installées</Text>
        <Text style={styles.infoValue}>{apps.length}</Text>
      </View>

      <View style={styles.infoItem}>
        <Text style={styles.infoLabel}>Domaines bloqués</Text>
        <Text style={styles.infoValue}>{domainCount}</Text>
      </View>

      <View style={styles.infoItem}>
        <Text style={styles.infoLabel}>VPN Status</Text>
        <Text style={[styles.infoValue, { color: vpnEnabled ? '#4CAF50' : '#888' }]}>
          {vpnEnabled ? 'Actif' : 'Inactif'}
        </Text>
      </View>

      <View style={styles.settingDivider} />

      <TouchableOpacity
        style={styles.dangerButton}
        onPress={() => {
          Alert.alert(
            'Réinitialiser',
            'Supprimer toutes les règles et paramètres?',
            [
              { text: 'Annuler', style: 'cancel' },
              {
                text: 'Réinitialiser',
                style: 'destructive',
                onPress: async () => {
                  await AsyncStorage.clear();
                  setBlockedApps({});
                  setBlockedDomains([]);
                  loadApps();
                  Alert.alert('OK', 'Paramètres réinitialisés');
                },
              },
            ]
          );
        }}
      >
        <Text style={styles.dangerButtonText}>🗑️ Réinitialiser tout</Text>
      </TouchableOpacity>
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
  appProtected: {
    fontSize: 12,
    color: '#4CAF50',
    fontWeight: '500',
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
  // DNS tab styles
  addDomainContainer: {
    flexDirection: 'row',
    marginBottom: 16,
  },
  domainInput: {
    flex: 1,
    backgroundColor: '#2a2a4e',
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: '#fff',
    fontSize: 16,
    marginRight: 8,
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
  subTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#888',
    marginTop: 20,
    marginBottom: 8,
  },
  domainList: {
    flex: 1,
  },
  domainItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#2a2a4e',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 8,
    marginBottom: 8,
  },
  domainText: {
    color: '#fff',
    fontSize: 14,
    flex: 1,
  },
  removeButton: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: '#ff4444',
    alignItems: 'center',
    justifyContent: 'center',
  },
  removeButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
  },
  // Logs tab styles
  logsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  logsCount: {
    color: '#888',
    fontSize: 14,
  },
  logsButtons: {
    flexDirection: 'row',
    marginBottom: 16,
    gap: 8,
  },
  refreshButton: {
    flex: 1,
    backgroundColor: '#2a2a4e',
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  refreshButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  clearButton: {
    flex: 1,
    backgroundColor: '#4a2a2a',
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  clearButtonText: {
    color: '#ff6b6b',
    fontSize: 14,
    fontWeight: '600',
  },
  warningBox: {
    backgroundColor: '#4a3a2a',
    padding: 12,
    borderRadius: 8,
    marginBottom: 16,
  },
  warningText: {
    color: '#ffaa00',
    fontSize: 14,
    textAlign: 'center',
  },
  logHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  logApp: {
    fontSize: 12,
    color: '#888',
    marginTop: 4,
    fontStyle: 'italic',
  },
  // Settings tab styles
  settingInfo: {
    flex: 1,
  },
  settingDesc: {
    fontSize: 12,
    color: '#888',
    marginTop: 2,
  },
  settingDivider: {
    height: 1,
    backgroundColor: '#2a2a4e',
    marginVertical: 20,
  },
  settingSection: {
    fontSize: 14,
    fontWeight: '600',
    color: '#888',
    marginBottom: 12,
    textTransform: 'uppercase',
  },
  infoItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
  },
  infoLabel: {
    fontSize: 14,
    color: '#aaa',
  },
  infoValue: {
    fontSize: 14,
    color: '#fff',
    fontWeight: '600',
  },
  dangerButton: {
    backgroundColor: '#4a2a2a',
    paddingVertical: 14,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 20,
  },
  dangerButtonText: {
    color: '#ff6b6b',
    fontSize: 14,
    fontWeight: '600',
  },
  infoBox: {
    backgroundColor: '#2a3a4e',
    padding: 16,
    borderRadius: 8,
    marginTop: 20,
  },
  infoBoxText: {
    color: '#aaddff',
    fontSize: 14,
    lineHeight: 20,
  },
});
