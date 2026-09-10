import React, { useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Form, InputGroup, ListGroup } from 'react-bootstrap';
import api from '../services/api';

function UserSearch() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);

  const handleSearch = useCallback(async (e) => {
    const value = e.target.value;
    setQuery(value);
    if (value.trim().length < 2) {
      setResults([]);
      return;
    }
    try {
      const response = await api.get(`/api/users/search?q=${encodeURIComponent(value.trim())}`);
      if (response.data.success) setResults(response.data.data.users);
    } catch {
      setResults([]);
    }
  }, []);

  return (
    <div className="position-relative" style={{ minWidth: '200px' }}>
      <InputGroup size="sm">
        <InputGroup.Text style={{ backgroundColor: 'transparent', borderColor: 'rgba(255,255,255,0.3)', color: '#fff' }}>
          <i className="bi bi-search"></i>
        </InputGroup.Text>
        <Form.Control
          type="text"
          placeholder="Buscar usuarios..."
          value={query}
          onChange={handleSearch}
          className="text-white placeholder-white"
          style={{
            backgroundColor: 'rgba(255,255,255,0.15)',
            borderColor: 'rgba(255,255,255,0.3)',
            color: '#ffffff',
            borderRadius: '0 8px 8px 0',
          }}
        />
      </InputGroup>

      {results.length > 0 && (
        <ListGroup
          className="position-absolute w-100 mt-1 shadow"
          style={{ zIndex: 1050, borderRadius: '8px', overflow: 'hidden' }}
        >
          {results.map((user) => (
            <ListGroup.Item key={user.id} className="p-2">
              <Link
                to={`/perfil/${user.id}`}
                className="d-flex align-items-center gap-2 text-decoration-none"
                onClick={() => { setQuery(''); setResults([]); }}
              >
                <div
                  className="rounded-circle d-inline-flex align-items-center justify-content-center"
                  style={{
                    width: '32px',
                    height: '32px',
                    backgroundColor: (user.profile_photo || user.profilePhoto) ? 'transparent' : 'rgba(21, 189, 15, 0.1)',
                    border: '1px solid var(--color-primary)',
                    overflow: 'hidden',
                    flexShrink: 0,
                  }}
                >
                  {user.profile_photo || user.profilePhoto ? (
                    <img src={user.profile_photo || user.profilePhoto} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    <i className="bi bi-person-fill" style={{ fontSize: '0.9rem', color: 'var(--color-primary)' }}></i>
                  )}
                </div>
                <span className="fw-medium" style={{ color: 'var(--color-text-dark)', fontSize: '0.9rem' }}>
                  {user.username}
                </span>
              </Link>
            </ListGroup.Item>
          ))}
        </ListGroup>
      )}
    </div>
  );
}

export default UserSearch;
