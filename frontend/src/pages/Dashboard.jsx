import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Flex,
  Text,
  Button,
  useColorModeValue,
  SimpleGrid,
  Avatar,
  Menu,
  MenuButton,
  MenuList,
  MenuItem,
  useToast,
  Spinner,
  Center,
  Badge
} from '@chakra-ui/react';
import * as FiIcons from 'react-icons/fi';

// Initial user data
const initialUser = {
  name: 'John Doe',
  email: 'john.doe@example.com',
  role: 'Admin',
  avatar: 'https://bit.ly/dan-abramov',
  lastLogin: new Date().toLocaleString()
};

// Stats configuration
const statsConfig = [
  { title: 'Total Users', value: '1,234', change: '+12%', icon: FiIcons.FiUsers },
  { title: 'Active Projects', value: '24', change: '+4%', icon: FiIcons.FiLayers },
  { title: 'Revenue', value: '$12,345', change: '+8.2%', icon: FiIcons.FiDollarSign },
  { title: 'Performance', value: '89.5%', change: '+2.3%', icon: FiIcons.FiActivity }
];

const Dashboard = () => {
  const [user] = useState(initialUser);
  const [loading, setLoading] = useState(false);
  const [stats] = useState(statsConfig);
  
  const navigate = useNavigate();
  const toast = useToast();
  const bgColor = useColorModeValue('gray.50', 'gray.900');
  const borderColor = useColorModeValue('gray.200', 'gray.700');
  const textColor = useColorModeValue('blue.600', 'blue.300');

  const handleLogout = useCallback(() => {
    localStorage.removeItem('token');
    navigate('/login');
    toast({
      title: 'Logged out successfully',
      status: 'success',
      duration: 2000,
      isClosable: true,
    });
  }, [navigate, toast]);

  useEffect(() => {
    const fetchUserData = async () => {
      try {
        setLoading(true);
        const token = localStorage.getItem('token');
        if (!token) {
          navigate('/login');
          return;
        }

        // Uncomment this when your backend is ready
        // const response = await axios.get('/api/users/me', {
        //   headers: { Authorization: `Bearer ${token}` }
        // });
        // setUser(prev => ({ ...prev, ...response.data }));
        
      } catch (error) {
        console.error('Error fetching user data:', error);
        toast({
          title: 'Session expired',
          description: 'Please login again',
          status: 'error',
          duration: 3000,
          isClosable: true,
        });
        handleLogout();
      } finally {
        setLoading(false);
      }
    };

    fetchUserData();
  }, [navigate, toast, handleLogout]);

  if (loading) {
    return (
      <Center h="100vh">
        <Spinner size="xl" />
      </Center>
    );
  }


  return (
    <Box minH="100vh" bg={bgColor}>
      {/* Header */}
      <Box bg={bgColor} px={6} py={4} borderBottom="1px" borderColor={borderColor} boxShadow="sm">
        <Flex justify="space-between" align="center">
          <Text fontSize="2xl" fontWeight="bold" color={textColor}>
            Qualtech AI
          </Text>
          
          <Flex align="center" gap={4}>
            <Button variant="ghost" p={2} rounded="full" position="relative">
              <FiIcons.FiBell size={20} />
              <Box
                as="span"
                position="absolute"
                top={1}
                right={1}
                w={2}
                h={2}
                bg="red.500"
                rounded="full"
              />
            </Button>
            
            <Menu>
              <MenuButton>
                <Flex align="center" gap={2} cursor="pointer">
                  <Avatar size="sm" name={user.name} src={user.avatar} />
                  <Box textAlign="left" display={{ base: 'none', md: 'block' }}>
                    <Text fontSize="sm" fontWeight="medium">{user.name}</Text>
                    <Text fontSize="xs" color="gray.500">{user.role}</Text>
                  </Box>
                </Flex>
              </MenuButton>
              <MenuList>
                <MenuItem icon={<FiIcons.FiUser size={16} />}>
                  Profile
                </MenuItem>
                <MenuItem icon={<FiIcons.FiSettings size={16} />}>
                  Settings
                </MenuItem>
                <MenuItem 
                  icon={<FiIcons.FiLogOut size={16} />} 
                  color="red.500"
                  onClick={handleLogout}
                >
                  Logout
                </MenuItem>
              </MenuList>
            </Menu>
          </Flex>
        </Flex>
      </Box>

      {/* Main Content */}
      <Box p={6} maxW="7xl" mx="auto">
        <Flex justify="space-between" align="center" mb={8}>
          <Box>
            <Text fontSize="2xl" fontWeight="bold" mb={1}>
              Welcome back, {user.name.split(' ')[0]}! 
            </Text>
            <Text color="gray.500">
              Here's what's happening with your projects today.
            </Text>
          </Box>
          <Button colorScheme="blue" size="sm">
            New Project
          </Button>
        </Flex>

        {/* Stats Grid */}
        <SimpleGrid columns={{ base: 1, md: 2, lg: 4 }} spacing={6} mb={8}>
          {stats.map((stat, index) => (
            <Box
              key={index}
              p={6}
              bg={bgColor}
              rounded="lg"
              border="1px"
              borderColor={borderColor}
              boxShadow="sm"
            >
              <Flex justify="space-between" align="center" mb={2}>
                <Box>
                  <Text fontSize="sm" color="gray.500" fontWeight="medium">
                    {stat.title}
                  </Text>
                  <Flex align="baseline" mt={1}>
                    <Text fontSize="2xl" fontWeight="bold" mr={2}>
                      {stat.value}
                    </Text>
                    <Badge colorScheme={stat.change.startsWith('+') ? 'green' : 'red'}>
                      {stat.change}
                    </Badge>
                  </Flex>
                </Box>
                <Box
                  p={3}
                  bg="blue.50"
                  color="blue.500"
                  rounded="lg"
                >
                  <stat.icon size={24} />
                </Box>
              </Flex>
            </Box>
          ))}
        </SimpleGrid>
      </Box>
    </Box>
  );
};

export default Dashboard;