import { useState, useEffect } from 'react';

function buildInitialValues(properties, editData) {
  const values = {};
  for (const p of properties) {
    if (editData && editData[p.name] != null) {
      values[p.name] = editData[p.name];
    } else if (p.defaultValue != null && p.defaultValue !== '') {
      values[p.name] = p.defaultValue;
    } else if (p.type === 'boolean') {
      values[p.name] = false;
    } else {
      values[p.name] = '';
    }
  }
  return values;
}

export default function ObjectForm({ properties, editData, onSubmit, onCancel }) {
  const [values, setValues] = useState(() => buildInitialValues(properties, editData));
  const isEdit = !!editData;

  useEffect(() => {
    setValues(buildInitialValues(properties, editData));
  }, [properties, editData]);

  function handleChange(name, value) {
    setValues((prev) => ({ ...prev, [name]: value }));
  }

  function handleSubmit(e) {
    e.preventDefault();
    // Convert types
    const payload = {};
    for (const p of properties) {
      let v = values[p.name];
      if (v === '' || v == null) {
        if (!isEdit) continue; // skip empty on create
        payload[p.name] = null;
        continue;
      }
      if (p.type === 'integer') v = parseInt(v, 10);
      else if (p.type === 'decimal') v = parseFloat(v);
      else if (p.type === 'boolean') v = v === true || v === 'true';
      payload[p.name] = v;
    }
    onSubmit(payload);
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <h3 className="font-semibold text-gray-800 text-sm mb-3">
        {isEdit ? '編輯物件' : '新增物件'}
      </h3>

      {properties.map((p) => (
        <div key={p.name} className="flex items-center gap-3">
          <label className="w-36 text-xs text-gray-600 text-right shrink-0">
            {p.name}
            {p.required && <span className="text-red-500 ml-0.5">*</span>}
          </label>

          {p.type === 'boolean' ? (
            <input
              type="checkbox"
              checked={values[p.name] === true || values[p.name] === 'true'}
              onChange={(e) => handleChange(p.name, e.target.checked)}
              className="h-4 w-4"
            />
          ) : p.type === 'enum' && p.enumValues ? (
            <select
              value={values[p.name] || ''}
              onChange={(e) => handleChange(p.name, e.target.value)}
              required={p.required}
              className="border border-gray-300 rounded px-2 py-1 text-sm flex-1"
            >
              <option value="">-- 選擇 --</option>
              {p.enumValues.map((ev) => (
                <option key={ev} value={ev}>
                  {ev}
                </option>
              ))}
            </select>
          ) : p.type === 'timestamp' ? (
            <input
              type="datetime-local"
              value={values[p.name] || ''}
              onChange={(e) => handleChange(p.name, e.target.value)}
              className="border border-gray-300 rounded px-2 py-1 text-sm flex-1"
            />
          ) : (
            <input
              type={p.type === 'integer' || p.type === 'decimal' ? 'number' : 'text'}
              step={p.type === 'decimal' ? '0.01' : undefined}
              value={values[p.name] ?? ''}
              onChange={(e) => handleChange(p.name, e.target.value)}
              required={p.required}
              disabled={isEdit && p.name === 'id'}
              className="border border-gray-300 rounded px-2 py-1 text-sm flex-1 disabled:bg-gray-100"
            />
          )}
        </div>
      ))}

      <div className="flex gap-2 pt-2 ml-36 pl-3">
        <button
          type="submit"
          className="bg-indigo-600 text-white px-4 py-1.5 rounded text-sm font-medium hover:bg-indigo-700"
        >
          {isEdit ? '更新' : '新增'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="border border-gray-300 text-gray-600 px-4 py-1.5 rounded text-sm hover:bg-gray-50"
        >
          取消
        </button>
      </div>
    </form>
  );
}
