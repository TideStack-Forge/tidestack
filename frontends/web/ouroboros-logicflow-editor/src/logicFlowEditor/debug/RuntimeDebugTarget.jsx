import React from 'react'

export default function RuntimeDebugTarget({ targets, runtimeId, disabled, onChange, label = '运行目标', placeholder = '请选择运行目标' }) {
  return <label className="logicflow-debug-target"><span>{label}</span><select value={runtimeId} onChange={(event) => onChange(event.target.value)} disabled={disabled}>
    <option value="">{placeholder}</option>
    {targets.map((target) => <option key={target.runtimeId} value={target.runtimeId} disabled={!target.supported}>
      {target.label} · {target.backend}{target.supported ? '' : ' · 需要更新助手'}
    </option>)}
  </select></label>
}
