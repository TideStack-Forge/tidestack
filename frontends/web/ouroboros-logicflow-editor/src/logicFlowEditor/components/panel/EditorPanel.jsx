import React from 'react'

export default function EditorPanel({ side, visible = true, header, children, className = '', ...props }) {
  return <div {...props}
    className={`${side === 'left' ? 'left-panel' : 'properties-panel'} ${visible ? '' : 'collapsed'} ${className}`.trim()}
    data-panel-visible={visible ? 'true' : 'false'}>
    <div className={side === 'left' ? 'left-panel-header' : 'header'}>{header}</div>
    {children}
  </div>
}
