import React from 'react';
import './LoadingSpinner.css';

const LoadingSpinner = ({ size = 'md' }) => (
  <div className={`spinner spinner-${size}`}>
    <div className="spinner-circle"></div>
  </div>
);

export default LoadingSpinner;
