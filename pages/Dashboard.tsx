
import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

const FeatureCard: React.FC<{ 
  title: string; 
  image: string; 
  onClick: () => void;
}> = ({ title, image, onClick }) => (
  <div 
    onClick={onClick}
    className="relative group overflow-hidden rounded-[2rem] cursor-pointer aspect-[1.4/1] border border-slate-800 hover:border-emerald-500/30 transition-all duration-500 bg-slate-900 shadow-2xl"
  >
    <div className="absolute inset-0 bg-slate-800 animate-pulse -z-20" />
    <img 
      src={image} 
      alt="" 
      className="absolute inset-0 w-full h-full object-cover transition-transform duration-700 group-hover:scale-110 opacity-60 group-hover:opacity-80 z-0"
      onError={(e) => {
        (e.target as HTMLImageElement).style.display = 'none';
      }}
    />
    <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/40 to-transparent flex flex-col justify-end p-8 z-10">
      <h3 className="text-2xl font-bold text-white tracking-tight group-hover:text-emerald-400 transition-colors leading-tight max-w-[90%] drop-shadow-lg">
        {title}
      </h3>
    </div>
  </div>
);

const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();

  const features = [
    { 
      id: 'crop', 
      title: t('dashboard.cards.crop_detect'), 
      image: 'https://images.unsplash.com/photo-1523348837708-15d4a09cfac2?auto=format&fit=crop&q=80&w=800', 
      path: '/crop-disease' 
    },
    { 
      id: 'livestock', 
      title: t('dashboard.cards.livestock'), 
      image: 'https://images.unsplash.com/photo-1500595046743-cd271d694d30?auto=format&fit=crop&q=80&w=800', 
      path: '/livestock' 
    },
    { 
      id: 'market', 
      title: t('dashboard.cards.market'), 
      image: 'https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&q=80&w=800', 
      path: '/market-insights' 
    },
    { 
      id: 'iot', 
      title: t('dashboard.cards.iot'), 
      image: 'https://etimg.etb2bimg.com/photo/109715182.cms', 
      path: '/iot' 
    },
    { 
      id: 'schemes', 
      title: t('dashboard.cards.schemes'), 
      image: 'https://images.moneycontrol.com/static-mcnews/2023/05/Parliament_building_1684893413434_1684893413549-770x435.png?impolicy=website&width=770&height=431', 
      path: '/schemes' 
    },
    { 
      id: 'loans', 
      title: t('dashboard.cards.loans'), 
      image: 'https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&q=80&w=800', 
      path: '/loans' 
    },
    { 
      id: 'traders', 
      title: t('dashboard.cards.traders'), 
      image: 'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&q=80&w=800', 
      path: '/traders' 
    },
    { 
      id: 'carbon', 
      title: t('dashboard.cards.carbon'), 
      image: 'https://images.unsplash.com/photo-1511497584788-876760111969?auto=format&fit=crop&q=80&w=800', 
      path: '/carbon' 
    },
    { 
      id: 'prices', 
      title: t('dashboard.cards.prices'), 
      image: 'https://images.moneycontrol.com/static-mcnews/2022/05/market-8-770x433.jpg', 
      path: '/live-prices' 
    },
  ];

  return (
    <div className="space-y-16 py-12 animate-in fade-in slide-in-from-bottom-8 duration-1000">
      <div className="relative text-center max-w-4xl mx-auto space-y-4">
        <h1 className="text-6xl md:text-7xl font-extrabold text-white tracking-tight">
          {t('dashboard.title')}
        </h1>
        <p className="text-xl md:text-2xl text-slate-400 font-medium">
          {t('dashboard.subtitle')}
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
        {features.map((f) => (
          <FeatureCard 
            key={f.id} 
            title={f.title} 
            image={f.image} 
            onClick={() => navigate(f.path)} 
          />
        ))}
      </div>
    </div>
  );
};

export default Dashboard;
