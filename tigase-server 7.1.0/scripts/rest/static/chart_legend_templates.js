var templateLegends = {
    bar : "<ul class=\"chart-<%=name.toLowerCase()%>-legend\"><% for (var i=0; i<datasets.length; i++){%><li style=\"background-color:<%=datasets[i].strokeColor%>\"><%if(datasets[i].label){%><%=datasets[i].label%><%}%></li><%}%></ul>",
    other : ""  
};
